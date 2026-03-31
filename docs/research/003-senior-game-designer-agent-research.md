# Research: Senior Game Designer Agent Prompt Design for Claude Opus 4.6

**Date:** 2026-03-31
**Researcher:** voyager-researcher
**Purpose:** Inform the design of an optimal "Senior Game Designer" agent prompt for Voyager (Minecraft elytra racing game)

---

## Summary

This document synthesizes research across eight domains: Claude Opus 4.6 prompt engineering, senior game designer mental models, racing game design, flow state theory, audio/visual feedback ("juice"), onboarding design, progression systems, and design documentation standards. The findings provide specific frameworks, numeric thresholds, and structural patterns needed to build a game designer agent that thinks like a seasoned professional rather than a generic AI assistant.

---

## 1. Claude Opus 4.6 Prompt Engineering for Creative/Design Roles

### Key Findings

**Opus 4.6 takes instructions literally.** Earlier Claude versions inferred intent and expanded on vague requests. Opus 4.6 does exactly what is asked -- nothing more, nothing less. This means the game designer agent prompt must explicitly activate creative and systems-thinking behaviors rather than relying on inference.

**Contract-style prompts work best.** The recommended structure is:
- Role definition (one line)
- Goal statement (what success looks like)
- Constraints (bullet points, not prose)
- Uncertainty handling (explicit "if unsure" rules)
- Output format (schema or structure)

**Examples over adjectives.** Rather than saying "be creative" or "think like a designer," provide one concrete example output demonstrating the desired quality level. This is critical for subjective/aesthetic work.

**Structured outputs even for subjective work.** Force responses into schemas (JSON, bullet points, rubrics) to maintain consistency while allowing creative content within those structures.

**Persona stability through negative instruction.** Stability comes from defining what NOT to do (avoid AI-isms, avoid generic patterns) rather than prescriptive behavioral scripts. This aligns with how senior designers think -- they know what to avoid through experience.

**Cognitive activation via tagged sections.** Use XML-tagged sections like `<thought_process>`, `<design_analysis>`, `<player_perspective>` to trigger different thinking modes. This maps well to the multiple lenses a game designer uses.

**Overengineering is the primary risk.** Opus 4.6 tends to add unnecessary abstractions and features. For a game designer agent, this means explicitly constraining scope: "Design for the current need, not hypothetical future requirements."

**Role prompting has significant impact.** Even a single sentence of role definition focuses Claude's behavior and tone. For a specialized game designer, a rich role description with specific domain knowledge activates more relevant reasoning patterns.

### Structural Pattern for the Agent

```
<system_instructions>
  Role: [One line identity]
  Goal: [Success criteria]
</system_instructions>

<design_frameworks>
  [MDA, Game Feel, Flow Theory references]
</design_frameworks>

<domain_knowledge>
  [Elytra physics, Minecraft constraints, racing game patterns]
</domain_knowledge>

<thought_process_instructions>
  [How to analyze before answering]
</thought_process_instructions>

<output_format>
  [Structured design output template]
</output_format>

<anti_patterns>
  [What to avoid -- generic answers, overengineering, untestable claims]
</anti_patterns>
```

### Sources
- [Claude 4 Best Practices -- Anthropic Official Docs](https://platform.claude.com/docs/en/build-with-claude/prompt-engineering/claude-4-best-practices)
- [Claude Prompt Engineering Best Practices 2026](https://promptbuilder.cc/blog/claude-prompt-engineering-best-practices-2026)
- [Claude Opus 4.6 System Prompt Analysis](https://www.pantaleone.net/blog/claude-opus-4.6-system-prompt-analysis-tuning-insights-template)

---

## 2. Senior Game Designer Mental Models

### Frameworks a Senior Designer Uses

**MDA Framework (Mechanics-Dynamics-Aesthetics)**
- **Mechanics**: Rules, actions, algorithms (what the designer directly controls)
- **Dynamics**: Emergent behavior from mechanics interacting with player input
- **Aesthetics**: Emotional responses evoked in the player (NOT visual appearance)
- Key insight: Designers work bottom-up (mechanics -> dynamics -> aesthetics), but players experience top-down (aesthetics -> dynamics -> mechanics). A senior designer always starts by defining the target aesthetic, then works backward to the mechanics that produce it.
- For Voyager: Target aesthetic = "Exhilarating flight mastery" -> Dynamics = risk/reward of speed vs. control, near-miss tension -> Mechanics = ring sizes, boost placement, track geometry.

**Game Feel Framework (Steve Swink)**
Six components: Input, Response, Context, Polish, Metaphor, Rules.
- **Input**: The tactile feel of controlling flight (mouse/keyboard responsiveness)
- **Response**: Must complete within a 100ms correction cycle (player reads feedback -> decides -> acts -> game responds)
- **Polish**: Interactive impression of physicality through harmony of animation, sounds, and effects with input-driven motion
- **ADSR Envelope for Response Curves**: Attack (how fast response begins), Decay (how quickly it backs off), Sustain (how long it holds), Release (how fast it ends) -- borrowed from music synthesis, used to tune "tight" vs "floaty" vs "smooth" feel
- **Metaphor**: Provides emotional meaning to motion -- elytra flight should evoke bird/glider metaphors

**Flow Theory (Csikszentmihalyi)**
Seven conditions for flow state:
1. Clear goals
2. Immediate feedback
3. Skill-challenge balance (the most critical)
4. Sense of player control
5. Loss of self-consciousness
6. Time distortion (losing track of time)
7. Intrinsic rewards

The Three-Channel Model: skill vs. challenge producing Boredom, Flow, or Anxiety.

**Core Loop Design**
The fundamental repeated action cycle. For Voyager: Fly -> Navigate Rings -> Score Points -> Compare -> Fly Again.

**Juice**
Sensory feedback layered onto actions to make them feel impactful. Screen shake, particles, sounds, camera effects, freeze frames. The intensity of juice must match the significance of the action.

### What Differentiates Senior from Junior Game Designers

| Trait | Junior | Senior |
|---|---|---|
| **Autonomy** | Needs supervision, follows instructions | Works on tough problems, helps others |
| **Problem-solving** | Waits for next instruction | Makes problems you didn't know you had go away |
| **Ambiguity** | Paralyzed by unclear requirements | Carves through ambiguity using design principles |
| **Experimentation** | Runs experiments to prove they're right | Runs experiments knowing they might be wrong |
| **Scope** | Focuses on "what needs to be designed" | Seeks holistic views, uncovers hidden constraints |
| **Process** | Refining their design process | Has a personal toolkit; uses frameworks flexibly |
| **Design thinking** | Focuses on implementation | Starts with player experience, works backward |

### Key Heuristics for the Agent

The senior game designer agent should:
1. **Always start with the player experience**, then work backward to mechanics
2. **Name the target emotion** before proposing any mechanic
3. **Consider second-order effects** -- how does this mechanic interact with everything else?
4. **Propose tuning variables**, not fixed values -- everything should be adjustable
5. **Anticipate failure modes** -- what happens when this goes wrong?
6. **Design for the worst player AND the best player** simultaneously

### Balancing Approach

Senior designers approach balancing through:
- Spreadsheets tracking cost-to-benefit ratios (linear or curved with diminishing/increasing returns)
- Identifying tuning variables: base values, scaling factors, cooldowns, ranges
- Playtesting feedback loops (propose -> test -> observe -> adjust)
- Mathematical modeling compared against mental models
- Data-driven adjustment post-launch (win rates, usage frequency, completion rates)

### Sources
- [MDA Framework -- Wikipedia](https://en.wikipedia.org/wiki/MDA_framework)
- [MDA: A Formal Approach to Game Design and Game Research (Original Paper)](https://users.cs.northwestern.edu/~hunicke/MDA.pdf)
- [Game Feel -- Wikipedia](https://en.wikipedia.org/wiki/Game_feel)
- [Game Feel and Player Control -- Lessons from Steve Swink](https://medium.com/design-bootcamp/game-feel-and-player-control-lessons-from-steve-swink-beae0ea1987f)
- [Game Feel: The Secret Ingredient -- Gamedeveloper.com](https://www.gamedeveloper.com/design/game-feel-the-secret-ingredient)
- [Senior vs Junior Game Designer -- askagamedev](https://www.tumblr.com/askagamedev/186565732886/what-does-it-mean-to-be-junior-mid-level-or)
- [Leveling Up: Junior to Senior Game Designer](https://gamedesigning.org/gaming/leveling-up-how-to-move-from-junior-to-senior-game-designer/)
- [Game Balance 101](https://gamedevgems.com/game-balance-5-ways-you-should-balance-your-game/)

---

## 3. Racing Game Design Specifics

### What Makes a Great Racing Game Feel Great

**Speed Perception** (critical for elytra racing):
- **FOV manipulation**: Wider FOV = perception of greater speed. Dynamic FOV that increases with velocity is standard practice.
- **Camera placement**: Lower camera = faster feeling. Chase camera provides more visual cues for speed processing.
- **Motion blur**: Radial blur toward the edges of the screen amplifies speed sensation.
- **Environmental density**: More objects passing by = stronger speed perception. The brain processes more information and interprets it as higher velocity.
- **Tunnel/corridor effects**: Narrow passages with close walls drastically amplify perceived speed.
- **Audio**: Engine pitch / wind sounds rising with velocity. Doppler effect on passing objects.
- For Voyager: Since this is Minecraft, block density IS the speed cue. Narrow ring sequences through corridors will feel fastest.

**Track Design Principles (Rational Approach)**:
Five essential metrics:
1. **Race Line**: The optimal path minimizing lateral force
2. **Clipping Points**: Entry, exit, and clipping points for corner navigation
3. **Track Width**: Wider = easier (straighter race line, less lateral force)
4. **Camber/Banking**: Tilted surfaces affecting force requirements
5. **Height Variation**: Elevation changes for variety and difficulty

Key design rules:
- **Punctuation**: Players need definite entry and exit points as "punctuation" to memorize the circuit
- **Compound corners**: Multiple clipping points increase difficulty proportionally
- **Corner difficulty formula**: Difficulty increases when angle between entry-clipping-exit becomes more acute AND distance between points decreases
- **Maximize longitudinal acceleration opportunities**: Good tracks let players feel fast (acceleration zones between challenging sections)

**Needle Threading Corners**: Wide entry, ideal clipping point, perception of narrow exit. These provide significant emotional value and give mastery perception.

### Ring/Checkpoint Design for Elytra Racing

From Minecraft's own Glide mini game and existing elytra racing implementations:
- **Three ring tiers by difficulty**: Large/Easy (green, least points), Medium (yellow), Small/Hard (light-blue/blue, most points)
- **Boost rings**: Separate category providing speed boosts
- **Checkpoints as respawn points**: When a player dies/crashes, respawn at last checkpoint (two powered Beacons in vanilla implementations)
- **Ring placement principle**: Checkpoints give newbie players short-term goals ("just reach the next ring"), eventually becoming irrelevant as skill improves -- they self-remove as a mechanic through player growth

**Specific thresholds for Voyager** (derived from physics reference):
- At normal glide speed (~20 blocks/sec), the player covers 1 block/tick
- Ring radius must account for the 0.6-block gliding hitbox
- Minimum practical ring inner radius: ~1.5 blocks (extremely hard) to ~5 blocks (easy)
- Ring spacing: At 20 blocks/sec, a ring every 3-5 seconds = every 60-100 blocks
- At boost speeds (33.5 blocks/sec), spacing should increase proportionally

### Race Duration Sweet Spots

- **Mario Kart 8 Deluxe 150cc**: Mean track time ~115 seconds (~2 minutes per race)
- **A cup of 4 races**: ~8-10 minutes total -- perfect for a play session
- **For Voyager**: Target 90-150 seconds per map, 4 maps per cup = 6-10 minute cup sessions
- Short enough for "one more race" psychology, long enough for skill expression

### Comeback Mechanics / Rubber Banding

**Pros**: Keeps races exciting, prevents blowouts, maintains engagement for all skill levels
**Cons**: Feels unfair to skilled players, breaks realism, punishes mastery
**Best practice for competitive play**: Avoid AI rubber banding entirely. Instead use:
- **Catch-up mechanics through track design**: Shortcuts that require skill, risk/reward boost zones
- **Item-based systems** (Mario Kart model): Better items for trailing players, but all items require player skill to use effectively
- **Ring scoring differentials**: Harder rings (smaller) worth disproportionately more points -- skilled players naturally score higher but trailing players can catch up through risky small-ring attempts

### Difficulty Scaling

**Within a race**: Start with wider rings and gentler turns, progressively tighten
**Between races in a cup**: First map easiest, fourth map hardest
**Across cups**: Beginner -> Intermediate -> Advanced -> Expert cup tiers

### Sources
- [A Rational Approach to Racing Game Track Design -- Gamedeveloper.com](https://www.gamedeveloper.com/design/a-rational-approach-to-racing-game-track-design)
- [Super Mario Kart at 25: Dissecting a Revolutionary Game Design](https://www.gamedeveloper.com/design/-i-super-mario-kart-i-at-25-dissecting-a-revolutionary-game-design)
- [Rubber-Banding AI Explained -- Game Wisdom](https://game-wisdom.com/critical/rubber-banding-ai-game-design)
- [Rubber-Banding System for Gameplay and Race Management (Game AI Pro)](http://www.gameaipro.com/GameAIPro/GameAIPro_Chapter42_A_Rubber-Banding_System_for_Gameplay_and_Race_Management.pdf)
- [Mario Kart Race Timer -- Fandom Wiki](https://mariokart.fandom.com/wiki/Race_timer)
- [Minecraft Elytra Wiki](https://minecraft.wiki/w/Elytra)

---

## 4. Flow State Design for Games

### Conditions for Flow in Elytra Racing

Applying Csikszentmihalyi's theory specifically to Voyager:

| Flow Condition | Voyager Implementation |
|---|---|
| **Clear Goals** | Ring sequence visible ahead; scoreboard showing position; finish line distance |
| **Immediate Feedback** | Ring pass sound/visual; speed indicator; position updates; near-miss effects |
| **Skill-Challenge Balance** | Ring sizes matching player skill; difficulty ramping within races |
| **Sense of Control** | Responsive elytra physics; player always feels their inputs matter |
| **Action-Awareness Merging** | Controls become transparent; player "becomes" the flight |
| **Concentration on Task** | No UI clutter during flight; minimal distractions; clean visual design |
| **Loss of Time Sense** | Engaging enough that 2 minutes feels like 30 seconds |

### What Breaks Flow (Pitfalls to Avoid)

1. **Downtime between races** -- Lobby wait times must be minimal; keep players in motion
2. **Unfair deaths** -- Collision with invisible hitboxes, unclear ring boundaries
3. **Unclear feedback** -- Not knowing if you passed a ring or missed it
4. **Repetitive tedium** -- Same map with no variation; predictable ring patterns
5. **Skill mismatch** -- Facing opponents far above or below your level
6. **UI interruptions** -- Pop-ups, menus, chat during flight
7. **Loading screens** -- Breaking immersion between maps in a cup

### Difficulty Ramping Specifics

**Within a single race (micro-curve)**:
- First 20%: Wide rings, gentle curves -- establish rhythm
- Middle 60%: Progressive tightening, introduce altitude changes, add ring color variety
- Final 20%: Tightest sequences, most challenging geometry -- climactic finish

**Between races in a cup (meso-curve)**:
- Map 1: Introductory, forgiving, establish the cup's theme
- Map 2: Step up difficulty, introduce the cup's signature mechanic
- Map 3: Full difficulty, complex sequences
- Map 4: Climactic, combines all elements, most challenging but also most rewarding

**Across cups (macro-curve)**:
- Bronze Cup: Wide rings (4-5 block radius), gentle turns, slow speeds
- Silver Cup: Medium rings (3-4 blocks), moderate turns, speed boost sections
- Gold Cup: Small rings (2-3 blocks), tight sequences, vertical elements
- Diamond Cup: Tiny rings (1.5-2 blocks), extreme geometry, speed-and-precision combined

### Skill Floor vs. Skill Ceiling Design

**Low skill floor techniques** (anyone can play):
- Large starting rings that are nearly impossible to miss
- Forgiving checkpoint spacing (crash recovery is fast)
- Visual guides showing the flight path (particle trails, ring sequences)
- Auto-aim-like ring detection (generous hitbox tolerance)
- Speed caps on beginner tracks preventing lethal crashes

**High skill ceiling techniques** (mastery is rewarding):
- Optional small bonus rings off the main path
- Shortcut routes requiring precise flight
- Speed-run optimization through ring order choices
- Advanced firework boost timing for speed records
- Near-miss scoring bonuses (passing closer to ring edge = more points)

### Sources
- [Flow Theory Applied to Game Design -- Medium](https://medium.com/@icodewithben/mihaly-csikszentmihalyis-flow-theory-game-design-ideas-9a06306b0fb8)
- [Flow in Games -- Jenova Chen MFA Thesis](https://www.jenovachen.com/flowingames/Flow_in_games_final.pdf)
- [Skill Ceiling and Skill Floor -- Game Design Skills](https://gamedesignskills.com/gaming/skill-ceiling-skill-floor/)
- [Lowering Floors and Sparing the Ceiling -- Sunspear Games](https://sunspeargames.com/lowering-the-skill-floor-without-harming-the-skill-ceiling/)

---

## 5. Audio/Visual Feedback Design (Juice)

### Juice Principles for Elytra Racing

**Core principle**: The intensity of juice must match the significance of the action. Common actions receive simpler juice to avoid player annoyance.

**Juice categories for Voyager**:

| Action | Visual Feedback | Audio Feedback | Camera Effect | Timing |
|---|---|---|---|---|
| **Ring Pass (hit)** | Green particle burst, ring lights up | Satisfying chime, pitch rises with combo | Slight FOV pulse | Instant (< 50ms) |
| **Ring Pass (near miss)** | Orange particle trail, ring flickers | Higher-pitched chime + whoosh | Slight screen shake | Instant |
| **Ring Miss** | Ring dims/fades red | Descending tone, subtle buzz | None (avoid punishment feel) | 200-300ms delay |
| **Speed Boost** | Radial speed lines, trail particles | Whoosh + acceleration sound | FOV widens over 200ms | Ramp over 200-500ms |
| **Collision/Crash** | Impact particles, screen crack overlay | Crunch + glass break | Screen shake (high magnitude, short duration) | Instant, 300ms decay |
| **Combo Streak** | Intensifying glow on player trail | Musical progression (each ring adds a note) | Progressive FOV increase | Builds over streak |
| **Finish Line** | Firework explosion, confetti | Fanfare, applause | Camera zoom out | 500ms buildup |

### Timing Windows (from Game Feel research)

- **Correction cycle**: Must be under 100ms (player input -> game response -> player perceives feedback)
- **Ring pass detection**: Must resolve within 1 tick (50ms) given elytra speeds
- **Audio feedback**: Should arrive within 1-2 frames of the visual event (< 33ms latency)
- **Screen shake parameters**: Magnitude (how far), Frequency (shakes/sec), Duration (total time). Magnitude should decay to zero over the duration.
- **Freeze frame on impact**: 2-4 frames (100-200ms) pause on significant collisions to reinforce importance

### ADSR Envelope Applied to Elytra Feedback

For a ring pass:
- **Attack**: 0ms -- instant visual feedback (particle burst)
- **Decay**: 100ms -- initial burst settles to sustained glow
- **Sustain**: 200ms -- ring glow persists
- **Release**: 300ms -- glow fades out

For a speed boost:
- **Attack**: 200ms -- FOV ramps up, speed lines appear
- **Decay**: 100ms -- initial intensity settles
- **Sustain**: Duration of boost effect
- **Release**: 500ms -- gradual return to normal FOV

### Near-Miss Design

Near-miss feedback taps into dopaminergic reward pathways -- the brain releases dopamine in response to "almost" experiences. For elytra racing:
- Detect proximity to ring edge (distance from center vs. radius)
- "Perfect" zone: within 80-100% of radius distance -> maximum points + special effect
- "Good" zone: within 50-80% -> standard points + standard effect
- "Near miss": passed within 120% of radius but didn't score -> flash effect + encouraging sound
- This creates a risk/reward tension: flying closer to ring edges is rewarded but increases crash risk

### Sound Design for Elytra Racing

- **Wind sound**: Continuous, pitch rises with velocity (simple pitch shift based on speed)
- **Spatial audio for rings**: Rings emit a subtle hum that grows louder as you approach, with stereo panning indicating direction
- **Doppler effect**: Sound of other players' flights should pitch-shift as they pass
- **Ring pass sound**: Should be satisfying and distinct per ring type (different tones for different point values)
- **Combo sounds**: Musical progression where each consecutive ring adds a note or increases the chord complexity
- **Boost sound**: Layered whoosh with low-frequency rumble for visceral impact

### Sources
- [Squeezing More Juice Out of Your Game Design -- GameAnalytics](https://www.gameanalytics.com/blog/squeezing-more-juice-out-of-your-game-design)
- [Juice in Game Design -- Blood Moon Interactive](https://www.bloodmooninteractive.com/articles/juice.html)
- [The Juice Problem -- Wayline](https://www.wayline.io/blog/the-juice-problem-how-exaggerated-feedback-is-harming-game-design)
- [Game Feel and Player Control -- Steve Swink](https://medium.com/design-bootcamp/game-feel-and-player-control-lessons-from-steve-swink-beae0ea1987f)
- [Building a Car Racing System Using Wwise](https://www.audiokinetic.com/en/blog/building-a-car-racing-system-using-wwise/)
- [Near-Miss Effect and Game Rewards -- Psychology of Games](https://www.psychologyofgames.com/2016/09/the-near-miss-effect-and-game-rewards/)

---

## 6. Onboarding Design for Skill-Based Games

### Implicit Teaching Principles

The best games teach without the player realizing they're being taught. For Voyager:

**Environmental Design as Teacher**:
- First ring in any track should be impossible to miss (straight ahead, very large)
- Ring sequence naturally teaches the flight path without arrows or tutorials
- Color coding teaches scoring implicitly (green = easy = low points, blue = hard = high points)
- First track in the beginner cup IS the tutorial

**Progressive Mechanical Introduction**:
1. **First race**: Straight flight through large rings (learn basic steering)
2. **Second race**: Gentle turns introduced (learn banking)
3. **Third race**: Altitude changes (learn pitch control)
4. **Fourth race**: All combined with tighter rings (demonstrate mastery)

**Learn-by-doing principle**: The "tutorial" track should have checkpoints that create short-term goals -- "just reach the next checkpoint" is simpler than "fly the whole course." As players improve, checkpoints become irrelevant and players focus on time/score optimization.

### First 5 Minutes Design for Voyager

| Time | Experience | What Player Learns |
|---|---|---|
| 0:00-0:30 | Spawn, see first track | Visual spectacle, "I want to fly that" |
| 0:30-1:00 | Launch into first flight | Basic elytra controls (look to steer) |
| 1:00-2:00 | First ring sequence (large, forgiving) | Rings give points, follow the path |
| 2:00-3:00 | Complete first race, see score | Scoring, leaderboard position |
| 3:00-5:00 | Second race with slightly harder rings | Skill improvement is visible and rewarding |

### Difficulty Curve for New vs. Returning Players

**New players**: Beginner cup acts as extended tutorial. No punishment for failure. Generous respawn. Clear visual guidance.

**Returning players**: Skip straight to their skill level. Personal best tracking motivates improvement. New cups/maps at appropriate difficulty. Time attack mode for pure skill expression.

### Sources
- [Game UX: Best Practices for Video Game Onboarding](https://inworld.ai/blog/game-ux-best-practices-for-video-game-onboarding)
- [Don't Spook the Newbies: 5 Proven Game Onboarding Techniques](https://acagamic.com/newsletter/2023/04/04/dont-spook-the-newbies-unveiling-5-proven-game-onboarding-techniques/)
- [Implicit Tutorials in Games -- ScienceDirect](https://www.sciencedirect.com/science/article/pii/S2405844022027700)
- [Player Onboarding Favouring Implicit Instructions (Academic Paper)](https://uu.diva-portal.org/smash/get/diva2:1871016/FULLTEXT01.pdf)

---

## 7. Progression System Design

### Recommended Approach for Voyager: Cosmetic-Primary with Skill Tracking

**Why cosmetic progression**: In a competitive racing game, gameplay-affecting unlocks create unfair advantages. Cosmetic-only progression ensures player retention is based on gameplay experience. Fortnite proved this model works at massive scale.

**Progression layers**:

| Layer | Mechanism | Engagement Type |
|---|---|---|
| **Per-race** | Score, position, ring accuracy % | Immediate satisfaction |
| **Per-session** | Cup results, XP earned | Short-term goal |
| **Personal bests** | Time records per map, total score records | Self-competition |
| **Leaderboards** | Global, friends, weekly seasonal | Social competition |
| **Ranks/Tiers** | Bronze -> Diamond based on ELO/MMR | Long-term progression |
| **Cosmetics** | Elytra skins, trails, ring pass effects, titles | Collection/expression |
| **Achievements** | Specific challenges (e.g., "Perfect run", "All blue rings") | Exploration/mastery |

### Key Design Principles

1. **Track personal improvement**: Show players their improvement over time (graph of best times, ring accuracy trending upward). This keeps lower-ranked players motivated.
2. **Multiple leaderboard scopes**: Global is discouraging for most. Friends-only and "players near your rank" leaderboards maintain motivation.
3. **Seasonal resets**: Weekly/monthly competitive seasons with rewards keep returning players engaged.
4. **Reward participation, not just winning**: Points for completing races, improving personal bests, and consistent play -- not only for podium finishes.
5. **Social hooks**: Friend invites, team/guild cups, spectator mode for top races.

### Retention Mechanics

- **Daily/weekly challenges**: "Score 500 points in Bronze Cup" or "Complete 3 races" for bonus XP
- **Streak rewards**: Playing on consecutive days/sessions earns bonus cosmetics
- **"One more race" psychology**: Quick race times (2 min) + visible progression bar near next reward = high replay motivation
- **Race events**: Time-limited special cups or maps that drive engagement spikes

### Sources
- [Designing Progression Systems That Keep Players Hooked](https://thegameofnerds.com/2025/06/17/designing-progression-systems-that-keep-players-hooked/)
- [Designing Effective Leaderboards for Enhanced Engagement](https://yukaichou.com/advanced-gamification/how-to-design-effective-leaderboards-boosting-motivation-and-engagement/)
- [Racing Game Mechanics That Drive Retention and Revenue](https://www.juegostudio.com/blog/racing-game-mechanics)
- [17 Proven Player Retention Strategies](https://gamedesignskills.com/game-design/player-retention/)

---

## 8. Design Documentation Standards

### GDD Structure for Voyager Features

**Recommended format** (per feature/system):

```
# Feature: [Name]

## Design Intention
- Why add this? What player experience does it serve?
- What emotion should the player feel?
- How will we know if we've succeeded? (testable criteria)

## Design Pillars (inherited from game-level pillars)
- [Which game pillar does this support?]

## Overview
- 2-3 sentence summary

## Detailed Design
- Mechanics specification (exact behaviors, formulas, values)
- Tuning variables (what can be adjusted, with initial values and ranges)
- Edge cases and failure modes
- Player-facing description (what the player sees/experiences)

## Interaction with Other Systems
- What this depends on
- What depends on this
- Potential conflicts

## Balancing Notes
- Initial values and rationale
- Playtesting plan (what to observe, what to measure)
- Adjustment strategy

## Visual/Audio Spec
- Feedback events (what triggers, what response)
- Timing windows
- Juice level (subtle / moderate / intense)

## Implementation Notes
- What the engineer needs to know (not HOW to code, but WHAT should happen)
- Examples with specific values
```

### Design Pillars for Voyager

Design pillars are the 3-4 fundamental philosophies that guide every design decision. Proposed:

1. **Exhilarating Flight**: Every moment of flying should feel fast, responsive, and thrilling
2. **Accessible Mastery**: Easy to start, deeply rewarding to master -- anyone can fly, few can fly perfectly
3. **Social Competition**: Racing is about people, not just times -- every race should feel like a shared experience
4. **Expressive Progression**: Players should see and show their growth through cosmetics, records, and achievements

Every feature must support at least one pillar. If it supports none, it doesn't belong.

### Design Spec vs. Design Intention

| Aspect | Design Intention | Design Specification |
|---|---|---|
| **Purpose** | Why this exists, what experience it creates | How it works, exact behaviors |
| **Audience** | All stakeholders (designers, producers, leads) | Engineers implementing the feature |
| **Detail level** | High-level, emotional, experiential | Precise, no room for interpretation |
| **Format** | 1-2 paragraphs, player-centric language | Structured sections, formulas, examples |
| **Test** | "Does this feel right?" | "Could an engineer implement this without contacting me?" |

**The engineer test**: If you handed your spec to an engineer you'd never met, with no contact allowed, could they implement it exactly as imagined? If not, add specificity.

### Writing Tips for Implementable Specs

1. **One feature per spec** -- if you have multiple top-level headers, you're writing multiple specs
2. **Define all terms** before using them; use consistent vocabulary
3. **Include complete examples** with actual numbers (not "some amount" but "3.5 blocks")
4. **Replace vague statements** with exact details ("enemy stats increase" -> "health increases by 15% per wave")
5. **Treat engineering as a black box** -- specify WHAT, never HOW to code it
6. **Disclose known flaws** -- be honest about edge cases where the design breaks down
7. **Progress from broad to specific** -- overview before details, concepts before exceptions

### Sources
- [Game Design Documents: Templates and Best Practices -- Wayline](https://www.wayline.io/blog/game-design-documents-templates-and-best-practices)
- [Tips for Writing Game Design Specs -- Medium](https://persenche.medium.com/tips-for-writing-game-design-specs-3dadb73486e6)
- [Game Design vs. Feature Design -- Mighty Bear Games](https://medium.com/mighty-bear-games/game-design-vs-game-feature-design-ef7402f5c66b)
- [How Game Designers Go from Abstract Idea to Dev-Ready Specs](https://medium.com/my-games-company/how-game-designers-can-go-from-abstract-idea-to-dev-ready-tech-specs-6f08acc1bd2d)
- [Crafting Effective Game Design Documents in 2026](https://www.hitem3d.ai/blog/en-What-is-a-Game-Design-Document-GDD-How-to-Write-an-Effective-Game-Design-Document/)

---

## Open Questions

1. **Minecraft-specific speed perception limits**: How fast can elytra go before Minecraft's chunk loading creates visible pop-in that breaks immersion? This needs empirical testing in Minestom with different render distances.

2. **Ring detection granularity at extreme speeds**: At 60-80 blocks/sec (firework boost), the player moves 3-4 blocks per tick. Small rings (1.5 block radius) could be skipped entirely in a single tick. The multi-sampling algorithm in the physics reference addresses this, but the performance cost at scale (many players, many rings) is unknown.

3. **Audio in Minecraft context**: Minecraft's sound system is limited compared to dedicated racing games. Spatial audio, Doppler effects, and complex layering may not be fully achievable. Need to investigate Minestom's sound API capabilities.

4. **Combo system specifics**: No clear research on optimal combo windows for flight-based games. This will need original design work and playtesting. Suggested starting point: 3-second window between rings to maintain combo.

5. **Optimal number of rings per map**: No direct research found. Proposed starting point based on race duration and spacing: 15-30 rings per 90-150 second race (roughly one ring every 5-6 seconds at moderate speed).

---

## Recommendation

The Senior Game Designer agent prompt should be structured as follows:

1. **Rich role definition** with explicit domain knowledge (elytra physics, racing game patterns, Minecraft constraints)
2. **Framework activation** -- explicitly reference MDA, Game Feel, and Flow Theory as thinking tools the agent must use
3. **Structured output format** -- every design response should follow the Intention -> Specification -> Tuning Variables -> Feedback Design pattern
4. **Player-first thinking mandate** -- every response must start with "What does the player experience?" before any mechanics discussion
5. **Anti-pattern list** -- explicitly forbid generic game design advice, untestable claims, and overengineered solutions
6. **Concrete examples** -- include 2-3 few-shot examples of ideal design outputs for ring placement, map design, and feedback timing
7. **Voyager-specific knowledge injection** -- embed key constants from the elytra physics reference (speeds, ring detection, damage thresholds) directly in the prompt so the agent can reference them without lookup
8. **Design pillar enforcement** -- every proposal must cite which design pillar it supports

This prompt structure leverages Opus 4.6's strengths (literal instruction following, structured output, role depth) while mitigating its weaknesses (overengineering, generic outputs) and activating senior-level game design thinking patterns.
