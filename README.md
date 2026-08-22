# frest's autism addons

An addon for [AUTISM Client](https://github.com/AutismDevelopment/Autism-Client).
Twenty-three modules in their own category, plus title screen branding.

## Building

The addon compiles against the AUTISM Client API from your local Maven repo, so
publish the client first:

```powershell
# in the Autism-Client checkout
.\gradlew.bat publishToMavenLocal --no-daemon

# then here
.\gradlew.bat build --no-daemon
```

The jar lands in `build/libs/`. Drop it in `.minecraft/mods` alongside the
client's own jar and Fabric API.

Versions are pinned in `gradle/libs.versions.toml`. If you publish a client
build that isn't `4.4-26.2-dev`, update `autism` there to match.

## Modules

Mining and building:

| Module | What it does |
| --- | --- |
| **AutoMine** | Mines whatever the crosshair is on, as if attack were held. |
| **VeinMiner** | Finishes the connected vein once you start breaking one ore. |
| **Surround** | Places blocks around your own feet. |

Farming and inventory:

| Module | What it does |
| --- | --- |
| **AutoFarm** | Harvests mature crops in reach and replants them. |
| **AutoBreed** | Feeds animals to breed them, shears sheep. |
| **AutoDrop** | Throws away configured junk. |
| **AutoRefill** | Tops up running-low hotbar stacks from your inventory. |
| **ChestStealer** | Empties or fills an already-open container. |
| **AutoSmelt** | Keeps an open furnace fed, collects output. |

Survival:

| Module | What it does |
| --- | --- |
| **AutoEat** | Eats to keep hunger and saturation topped up. |
| **AutoGapple** | Eats a golden apple when your health drops. |
| **AutoPot** | Throws splash healing at your feet when low. |
| **AutoMilk** | Drinks milk to clear a harmful effect. |
| **AutoShield** | Raises your shield against incoming projectiles. |
| **AutoBucket** | Water-clutches a long fall with a bucket you own. |
| **ElytraSwap** | Hotswaps elytra and chestplate on a keybind. |
| **AutoRespawn** | Clicks through the death screen. |

Movement and comfort:

| Module | What it does |
| --- | --- |
| **AutoSprint** | Sprints whenever you move forward. |
| **Bunnyhop** | Jumps automatically, on a Sprinting / Walking / Always condition. |
| **AntiEntityPush** | Stops players and mobs shoving you around. |
| **AntiWaterPush** | Stops flowing water and lava carrying you. |
| **AntiWobble** | Removes the nausea and portal screen distortion. |
| **BowSpam** | Releases the bow at a chosen draw time and redraws. |

Every module's settings render through the client's own module screen — there
is no GUI code in this addon. `BoolSetting` becomes a toggle, `IntSetting` a
slider, `EnumSetting` a cycle button, and `.group(...)` a section header.

## Overlap with builtins

Three of these duplicate functionality the client already ships:

- **AutoSprint** vs builtin `sprint` — the builtin runs through
  `ModuleMovementUtil` with mixins and handles omnidirectional sprint and
  collision cases this one doesn't.
- **AutoMine** vs builtin `auto-clicker` — different scope, but they will fight
  over the attack key if both are on.
- **AntiWobble** vs `NoRender` → Overlay → Nausea — same injection points,
  same result. This is the standalone version.

## Mixins

Four, all with `require = 0`, so a mapping change degrades to "this module does
nothing" instead of a launch crash:

- `EntityPushMixin` — `LivingEntity.doPush`
- `WobbleMixin` — `GameRenderer.renderLevel`
- `WobbleOverlayMixin` — `Hud.extractConfusionOverlay` / `extractPortalOverlay`
- `TitleBrandingMixin` — `AutismTitleScreen.extractRenderState`

`TitleBrandingMixin` targets `AutismTitleScreen`, not vanilla's `TitleScreen`:
the client swaps the title screen for its own class, which extends `Screen`
directly and never calls `super.extractRenderState`.

AntiWaterPush used to carry a fifth mixin on `Entity.isPushedByFluid`. That
method doesn't exist in this mapping set, so it silently no-opped. It now works
through `preMovementTick()` and needs no mixin.

## Design notes

AutoFarm is capped at 4.5 blocks, vanilla survival reach, and the radius setting
can't be pushed past it. VeinMiner breaks through `continueDestroyBlock`, the
same progressive loop vanilla runs while you hold the button, so hardness and
tool speed apply normally. The Anti\*Push modules only drop effects the world
imposes on you — neither grants movement vanilla wouldn't allow, and both report
your true position.

BowSpam handles release timing only; you still aim. The draw is genuine, so a
short draw really does fire a weak arrow.

## CI

`.github/workflows/build.yml` checks out AUTISM Client, publishes its API, then
builds this addon and uploads the jar as an artifact. That's the only way to
build in CI — the client API isn't on any public Maven repo.

## Licence

AUTISM Client is GPL-3.0. This addon builds against its API, so matching that
licence is the straightforward choice; add a `LICENSE` file before publishing if
you want that to be explicit. Worth a look at the client's own terms first — I'm
not the person to give you a legal read on it.
