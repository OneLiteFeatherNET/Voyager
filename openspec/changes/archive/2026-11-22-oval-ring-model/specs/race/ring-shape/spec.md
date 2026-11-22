# Spec Delta

## Purpose

A ring is a circle or an oval. A circle is the disc the game has always used. An oval has a semi-major radius, a semi-minor
radius and an in-plane major axis, so a course can state a ring that is narrower on one side than on the other. This
capability says which points count as inside each shape, when a racer has passed a ring, and what the computer racers keep
as their clearance.

## ADDED Requirements

### Requirement: A ring is a circle or an oval
**Priority:** MoSCoW Must

THE SYSTEM SHALL represent a ring as a circle with one radius, or as an oval with a semi-major radius `radius`, a semi-minor
radius `radiusMinor` and a unit `majorAxis` that lies in the plane of the ring. THE SYSTEM SHALL represent an oval whose two
radii are equal as the circle of that radius.

#### Scenario: Oval with equal radii is a circle
- **WHEN** an oval is created with `radius` 4 and `radiusMinor` 4
- **THEN** the ring's shape is the circle of radius 4, and it equals a circle created with radius 4

#### Scenario: Ring created with a radius is a circle
- **WHEN** a ring is created with a radius and no oval fields
- **THEN** the ring is a circle of that radius, and its `radius()` returns that radius

### Requirement: A ring's radii and points are within bounds
**Priority:** MoSCoW Must

THE SYSTEM SHALL accept a ring only when each of its radii lies in `[0.5, 32]` blocks, both ends inclusive. The radii are the radius of
a circle, and the semi-major and the semi-minor radius of an oval. THE SYSTEM SHALL accept a ring only when its points lie in `[0, 100]`.
IF a radius or a point count lies outside these bounds, THEN THE SYSTEM SHALL refuse the ring, and the refusal SHALL name the value and the bounds.

#### Scenario: Circle radius below the bounds
- **WHEN** a circle is created with radius 0.49
- **THEN** the ring is refused, and the message names 0.49 and the range 0.5 to 32

#### Scenario: Circle radius at the bounds
- **WHEN** a circle is created with radius 32
- **THEN** the ring is accepted

#### Scenario: Oval semi-major radius above the bounds
- **WHEN** an oval is created with radius 33 and radiusMinor 3
- **THEN** the oval is refused and the message names 33 and the range 0.5 to 32

#### Scenario: Points above the bounds
- **WHEN** a ring is created with 101 points
- **THEN** the ring is refused, and the message names 101 and the range 0 to 100

### Requirement: A circle contains the points of its disc, rim included
**Priority:** MoSCoW Must

WHEN a point lies in the plane of a circular ring, THE SYSTEM SHALL decide that the point is inside the ring when its distance
from the centre is at most the radius. THE SYSTEM SHALL use this test for a circle without any other rule.

#### Scenario: Point on the rim is inside
- **WHEN** a point lies in the ring's plane at exactly the radius from the centre
- **THEN** the point is inside the ring

#### Scenario: Point just beyond the rim is outside
- **WHEN** a point lies in the ring's plane just farther from the centre than the radius
- **THEN** the point is outside the ring

### Requirement: An oval contains the points of its ellipse, rim included
**Priority:** MoSCoW Must

WHEN a point lies in the plane of an oval, THE SYSTEM SHALL take `d` as the point minus the centre, `u` as `d` dotted with
`majorAxis`, and `v` as `d` dotted with the cross product of the normal and `majorAxis`, and SHALL decide that the point is
inside when `(u / radius)` squared plus `(v / radiusMinor)` squared is at most 1.

#### Scenario: Axis-aligned oval
- **WHEN** an oval of `radius` 6 and `radiusMinor` 3 has its `majorAxis` along the first axis of its plane, and a point lies 5 along that axis and 0 across it
- **THEN** the point is inside, and a point that lies 0 along the axis and 3.5 across it is outside

#### Scenario: Tilted oval
- **WHEN** an oval has its `majorAxis` at 45 degrees within its plane and a point lies clearly inside its ellipse along that axis
- **THEN** the point is inside, and a point clearly outside along the minor axis is outside

#### Scenario: Rim is inclusive at the ends of the axes
- **WHEN** a point lies exactly at the end of the major axis, at distance `radius` along `majorAxis`
- **THEN** the point is inside

#### Scenario: Point inside the circumscribed circle but outside the oval
- **WHEN** a point lies at distance 5 from the centre along the minor axis of an oval with `radius` 6 and `radiusMinor` 3
- **THEN** the point is outside the oval, although it is inside the circle of radius 6

### Requirement: An oval must be valid
**Priority:** MoSCoW Must

IF an oval's semi-minor radius is greater than its semi-major radius, IF its `majorAxis` is not of unit length within 1e-9, or IF
its `majorAxis` is not perpendicular to the normal within 1e-6, THEN THE SYSTEM SHALL refuse the ring, and the refusal SHALL name the
value that is wrong. The bounds of the requirement "A ring's radii and points are within bounds" apply to an oval as well.

#### Scenario: Minor radius above the major radius
- **WHEN** an oval is created with `radius` 6 and `radiusMinor` 7
- **THEN** the oval is refused and the message names `radiusMinor`

#### Scenario: Major axis parallel to the normal
- **WHEN** an oval's `majorAxis` is the same as its normal
- **THEN** the ring is refused and the message names `majorAxis`

#### Scenario: Major axis not of unit length
- **WHEN** an oval's `majorAxis` has length 2
- **THEN** the oval is refused and the message names `majorAxis`

### Requirement: A racer passes an oval only through its area
**Priority:** MoSCoW Must

WHEN a racer's movement segment crosses the plane of a ring, THE SYSTEM SHALL count the ring as passed only when the crossing
point is inside the ring's shape, as the containment requirements of this capability decide.

#### Scenario: Segment through the oval's area
- **WHEN** a racer's segment crosses the plane of an oval at a point inside its ellipse
- **THEN** the ring is passed

#### Scenario: Segment through the circumscribed circle only
- **WHEN** a racer's segment crosses the plane of an oval at a point inside the circle of its semi-major radius but outside the ellipse
- **THEN** the ring is not passed

### Requirement: Circles keep their containment and collision
**Priority:** MoSCoW Must

THE SYSTEM SHALL give every circular ring the containment and the collision that the release before this capability gives
it, so that no ring that exists today changes how it is passed.

#### Scenario: Grid of points around a circle
- **WHEN** the containment of a circle is compared with the test `distanceTo(centre) <= radius` on a fixed grid of points in its plane
- **THEN** the two results agree for every point of the grid

### Requirement: Computer racers keep the outer bound as clearance
**Priority:** MoSCoW Should

WHEN a computer racer detours around a ring, THE SYSTEM SHALL measure the clearance from the ring's semi-major radius, so that the
detour clears the whole outline of an oval.

#### Scenario: Detour around an oval
- **WHEN** a computer racer detours around an oval whose semi-major radius is 6
- **THEN** the detour's clearance is measured from 6, the same clearance a circle of radius 6 would get
