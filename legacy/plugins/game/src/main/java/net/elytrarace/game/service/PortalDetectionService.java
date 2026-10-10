package net.elytrarace.game.service;

import net.elytrarace.game.model.GamePortalDTO;
import net.elytrarace.game.util.ElytraMetadata;
import org.apache.commons.geometry.euclidean.threed.Vector3D;
import org.apache.commons.geometry.euclidean.threed.line.Lines3D;
import org.apache.commons.numbers.core.Precision;
import org.bukkit.Bukkit;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.TreeSet;
import java.util.stream.Collectors;

public class PortalDetectionService {

    public static Precision.DoubleEquivalence PRECISION = Precision.doubleEquivalenceOfEpsilon(1e-6);

    static void handlePortalDetection(GameService gameService) {
        var currentMap = gameService.getCurrentMap();
        if (currentMap.isEmpty()) {
            return;
        }
        var map = currentMap.get();
        var portals = map.portals().stream().map(GamePortalDTO.class::cast).collect(Collectors.toCollection(TreeSet::new));
        if (portals.isEmpty()) {
            return;
        }

        Bukkit.getOnlinePlayers().forEach(player -> {
            if (player.hasMetadata(ElytraMetadata.LAST_POSITIONS)) {
                var metdata = player.getMetadata(ElytraMetadata.LAST_POSITIONS);
                var lastPositions = (Vector3D[]) metdata.getFirst().value();
                if (lastPositions == null || lastPositions.length == 0) {
                    return;
                }
                var position = lastPositions[0];
                var position2 = lastPositions[1];
                var position3 = lastPositions[2];

                if (position == null || position2 == null || position3 == null) {
                    return;
                }

                if (position.isZero(PRECISION) || position2.isZero(PRECISION) || position3.isZero(PRECISION)) {
                    return;
                }

                if (position.vectorTo(position2).isZero(PRECISION) || position2.vectorTo(position3).isZero(PRECISION) || position.vectorTo(position3).isZero(PRECISION)) {
                    return;
                }
                GamePortalDTO portal;

                if (player.hasMetadata(ElytraMetadata.LAST_PORTAL)) {
                    var portalIndex = player.getMetadata(ElytraMetadata.LAST_PORTAL).getFirst().asInt();
                    portal = portals.higher(portals.stream().filter(p -> p.index() == portalIndex).findFirst().orElse(portals.first()));
                } else {
                    portal = portals.first();
                }
                if (portal == null) {
                    return;
                }

                var firstLine = Lines3D.fromPoints(position, position2, PRECISION);
                var secondLine = Lines3D.fromPoints(position2, position3, PRECISION);
                var thirdLine = Lines3D.fromPoints(position, position3, PRECISION);

                var firstSegment = Lines3D.segmentFromPoints(position, position2, PRECISION);
                var secondSegment = Lines3D.segmentFromPoints(position2, position3, PRECISION);
                var thirdSegment = Lines3D.segmentFromPoints(position, position3, PRECISION);

                var firstIntersection = portal.plane().intersection(firstLine);
                var secondIntersection = portal.plane().intersection(secondLine);
                var thirdIntersection = portal.plane().intersection(thirdLine);

                var firstLineSuccess = firstIntersection != null && portal.bounds().contains(firstIntersection, PRECISION) && firstSegment.contains(firstIntersection) && portal.regionBSPTree3D().contains(firstIntersection);
                var secondLineSuccess = secondIntersection != null && portal.bounds().contains(secondIntersection, PRECISION) && secondSegment.contains(secondIntersection) && portal.regionBSPTree3D().contains(secondIntersection);
                var thirdLineSuccess = thirdIntersection != null && portal.bounds().contains(thirdIntersection, PRECISION) && thirdSegment.contains(thirdIntersection) && portal.regionBSPTree3D().contains(thirdIntersection);

                if (firstLineSuccess || secondLineSuccess || thirdLineSuccess) {
                    player.setMetadata(ElytraMetadata.LAST_PORTAL, new FixedMetadataValue(gameService.getPlugin(), portal.index()));
                    player.sendMessage("You have entered portal: " + portal.index());
                }
            }
        });
    }
}
