# Escape Pods

A sci-fi pixel-art overlay for a Minecraft Java Edition six-row chest GUI.

## Files

- `escape-pods.png`: **176 x 222 pixels**, with the Escape Pods title and Inventory label. Use this as the native-size overlay.
- `escape-pods-4x.png`: **704 x 888 pixels**, an exact nearest-neighbour enlargement for previewing. Render at 176 x 222 GUI units if using it in-game.
- `escape-pods-unlabelled.png`: the same native-size panel with blank label plates, for integrations which draw their own text.
- `layout.json`: exact item coordinates and vanilla atlas mapping.
- `align-gui.ps1`: reproducible Windows image alignment script; requires only System.Drawing.
- `generated-artwork.png`: original generated artwork, retained as the script's input. Its original spacing is not suitable for use as an overlay.
- `prompt.txt`: prompt used with the built-in image-generation tool. No fallback API/CLI was used. The final alignment, slot wells, and lettering were produced by the script with user approval.

## Placement

Draw the panel at the six-row chest GUI's top-left, at **176 x 222 logical GUI pixels**. Minecraft's GUI scale determines its on-screen size; use nearest-neighbour filtering. There is no external padding. Only the stepped outer corners are transparent; the panel and slot interiors are opaque. Render item stacks, hover highlights, and tooltips above it.

Use the labelled image when the overlay supplies the title. Use the unlabelled version when the integration supplies its own labels; use a light title colour on its dark title plate. The lighter Inventory plate remains readable under the vanilla Inventory label.

This is a rendered-screen overlay, **not a direct replacement for `generic_54.png`**. The vanilla file is a 256 x 256 atlas, and the client draws its lower section from texture y=126 to GUI y=125. Renaming this 176 x 222 PNG to that filename would misalign the lower slots. Installation requires your existing resource-pack/custom-GUI overlay mechanism; these files do not automatically install or open a menu.

## Slot coordinates

All coordinates are relative to the panel's top-left. Each item interior is 16 x 16 pixels, inside an 18 x 18 bevelled slot. The border begins one pixel before the item interior on each axis.

| Region | Columns | Item-left x | Item-top y |
| --- | --- | --- | --- |
| Chest | 9 | 8 + 18 x column | 18, 36, 54, 72, 90, 108 |
| Player inventory | 9 | 8 + 18 x column | 139, 157, 175 |
| Hotbar | 9 | 8 + 18 x column | 197 |

Column indices run from 0 through 8. The title starts at (8, 6), and the Inventory label starts at (8, 128).

Geometry was checked against the local vanilla client `ContainerScreen`, `ChestMenu`, and `AbstractContainerMenu` sources. The final PNG was visually inspected. It has not been previewed inside a running Minecraft client; no project build or tests were run.

## Regenerate aligned files

From this directory in Windows PowerShell:

```powershell
.\align-gui.ps1
```

The script reads `generated-artwork.png` and `layout.json` and replaces the three derived PNGs. Its measured source-coordinate map is specific to the retained original artwork.
