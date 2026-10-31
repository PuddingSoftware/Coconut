# Escape Pods — top chest panel

The revised artwork covers only the six-row, nine-column chest menu (54 empty cells). It contains no player inventory, Inventory label, or hotbar. The design uses navy glass, cyan illuminated brackets, violet accents, amber indicators, and a capsule emblem with custom ESCAPE PODS lettering.

## Files

- `escape-pods.png`: revised 1448 x 1086 RGBA artwork with transparent exterior, created with the built-in image-generation tool.
- `prompt-v2.txt`: generation prompt and corrective edit prompts.
- `previous-v1/`: original full-inventory artwork, native-size variants, layout metadata, and alignment script, retained together for reference.

## Integration boundary

This is artwork, not an installed resource pack or server menu implementation. No Java code or player inventory contents were changed. No code references to these assets were found in the project.

Use the new design only for the upper chest region. Keep the client's player inventory and hotbar rendering and item contents unchanged. Do not install the archived full-inventory images or use their alignment script for this revision.

The vanilla six-row chest's intended upper region is 176 x 125 logical GUI pixels. Item interiors start at x = 8 + 18 * column and y = 18 + 18 * row, with columns 0–8 and rows 0–5. Each item interior is 16 x 16 logical pixels. The player area begins at logical y = 125 and must remain outside the custom overlay.

The generated artwork is not a pixel-aligned native texture or a replacement for the vanilla `generic_54.png` atlas. Its frame and cell spacing need to be fitted to those coordinates during resource-pack integration; simply scaling the whole image is insufficient. Visual inspection verifies the six-by-nine grid and absence of the player area. In-game alignment, item hover regions, and rendering have not been tested.
