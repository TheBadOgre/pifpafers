# Army Editor — UI Plan Visual Specification

Source: `./ui_plan.png` — hand-drawn wireframe with detailed annotations (file relative to the spec md).  
Companion to: `2026-05-15-neuroshima-hex-army-editor-design.md`.

This document is the authoritative transcript of every visual and behavioral note in the wireframe image. Implementation agents must read this alongside the design spec to understand exact layout, widget placement, and interaction nuances that are not derivable from the domain model alone.

---

## Conceptual Notes from the Wireframe Header

These appear as a large annotation block at the top-left of the image.

**What the tool is:** An army planner for Neuroshima HEX. Allows drawing individual tokens; later a separate print-layout step (out of scope here) will arrange them on A4 pages. Goal: easy creation and modification of tokens.

**Two overloaded uses of the word "token":**
1. *Token (programming)*: a pointer/handle managed by the tool. Has a defined role tag (UNIT or MODIFIER). Manager-type tokens have a role of MOD with sub-properties.
2. *Token bag*: the list of tokens — basically what you edit and save in this tool.

**Architecture constraints noted on the wireframe:**
- Follow MVC. The CONTROLLER executes actions/operations. UI components have no logic beyond displaying state.
- Every action must be implemented as an `Action` (`Command`) object and must be undo/redoable.
- Actions are handled via the command layer, never directly from UI event handlers.
- Two languages: English (international default), Polish. Locale selected from OS. French mentioned as a future third.
- All icons live in resources; placeholders (solid black) are acceptable during development.
- Model classes (Token, Layer, LayerProperties) can be defined before the UI exists — the model is independent of Swing.

---

## Window Layout (Five Regions)

```
┌──────────────────────────────────────────────────────────────────┐
│  TOP BAR: rename field | save | save as | print                  │
├──────┬──────────────────┬──────────────────────────────┬─────────┤
│ TOOL │  TOKENS          │         CANVAS               │ LAYERS  │
│ STRIP│  COLLECTION      │                              │ (top)   │
│      │  (scrollable     │     token rendered here      ├─────────┤
│      │   grid)          │                              │ LAYER   │
│      │                  │   [ show overlay ☑ ]         │ PROPS   │
│      ├──────────────────┴──────────────────────────────┴─────────┤
│      │        ASSETS LIBRARY (folder tree | asset grid)          │
│      │        [ refresh content ]  [ size slider ]               │
└──────┴──────────────────────────────────────────────────────────-┘
```

---

## Top Bar

| Widget | Detail |
|--------|--------|
| **Rename token field** | Editable text input. Shows and sets the current token's name. Fires a rename command on commit (Enter or focus-lost). Undo/redo applies. |
| **Save** `Ctrl+S` | Saves current bag to its existing file path. If no path yet (new bag), behaves like Save As. |
| **Save As** | Prompts for file path/name. Separate from Save. |
| **Print** | Annotated as "list of Image pdf" — generates a PDF sheet. **Out of scope for this iteration** (stub only). |
| **Ask to save before closing** | If bag has unsaved changes and user closes or loads another, show "Save before closing?" dialog before proceeding. |

Last-saved timestamp is displayed somewhere in the bar or status area (wireframe shows `last saved: 14.05.2026 02:00`).

---

## Left Strip — Tool Palette

Six mutually exclusive radio-button tools arranged vertically:

1. **Select** — selects layers; no transform drag.
2. **Move** — translates selected layer(s) via drag on canvas.
3. **Rotate** — rotates selected layer(s) via drag on canvas.
4. **Scale** — scales selected layer(s) via drag on canvas.
5. **Opacity** — adjusts opacity via drag on canvas.
6. **Colorize** — adjusts hue/saturation/brightness via drag on canvas.

Active tool installs the canvas mouse handler and cursor. Exactly one tool active at all times.

---

## Left Panel — Tokens Collection

**Content:** Scrollable grid of token thumbnails. Each thumbnail is shaped according to token kind:
- UNIT tokens → hexagonal outline.
- MODIFIER tokens → circular outline.

Shape is visible in the thumbnail so the user can distinguish kinds at a glance without labels.

**Per-thumbnail interactions:**
- Click → selects the token; canvas updates to show it.
- Hover → reveals a remove (trash) button on the thumbnail.

**Remove token:**
- Clicking the trash icon triggers a confirmation pop-up before deletion. Pop-up is mandatory; no silent deletes.
- Deletion is undoable/redoable via the command stack.

**Bottom buttons (always visible, not scroll-dependent):**

| Button | Label in wireframe | Behavior |
|--------|--------------------|----------|
| Add unit | `+ unit_hex` (hex-shaped icon) | Creates a new empty UNIT token, appends to bag, selects it. Undoable. |
| Add modifier | `+ modifier_circle` (circle-shaped icon) | Creates a new empty MODIFIER token, appends to bag, selects it. Undoable. |

Button shape matches token kind — hex button for units, round button for modifiers.

**Size slider:** At the bottom of the panel. Controls thumbnail size (48–192 px). View state only — not in model, not undoable, persisted in user prefs.

---

## Center — Canvas

**Canvas area:** Renders the active token. Layers drawn bottom-to-top. Token shape outline (hex or circle) is always visible.

**Zoom & pan** (from design spec, confirmed in wireframe):
- Mouse wheel → zoom anchored at cursor.
- Middle-mouse drag → pan.
- Ctrl+0 → reset to 1:1 / fit.

**Helper overlay toggle:**
- Checkbox labeled `show overlay` positioned below the canvas.
- When checked: draws `HEX_template_lines.png` centered over the token, showing proportional guides and actual-size boundaries.
- Purpose (per annotation): "to see actual parts of token are visible + proportions, can be disabled/enabled."
- Toggle is view state; not a command; not undoable.

---

## Right Top — Current Token Layers

**Header:** "Current token layers"

**List:** Vertical list of layers for the active token. **Top of list = topmost layer in z-order** (index last in model, rendered last/on top). Scrollable.

**Each row contains:**
- Layer thumbnail.
- Visual indication of selection state.
- Per-row action buttons on the right edge (always visible, not hover-only):
  - Duplicate button
  - Remove button
  - Drag handle (for reorder)

### Layer Selection

| Action | Behavior |
|--------|----------|
| Click row | Selects that layer; deselects all others. |
| Ctrl+Click row | Adds/removes that layer from multi-selection. |
| ESC | Clears selection (deselects all layers). |

Multi-selection affects canvas tools (all selected layers move/rotate/scale together) and enables multi-layer drag reorder.

### Duplicate Layer

- Creates an exact copy of the selected layer (all props identical).
- The duplicated layer is inserted **above** the source layer (one index higher in z-order).
- Implemented as a command; fully undoable/redoable.

### Remove Layer

- Removes the selected layer from the token.
- Goes onto the undo stack — removal is fully undoable/redoable.
- No confirmation dialog required (unlike token removal).

### Drag Reorder

- Drag handle allows reordering layers by dragging up or down.
- Works for a single selected layer or multiple selected layers simultaneously (they move as a group).
- Reorder is implemented as a command; fully undoable/redoable.

**Size slider:** At the bottom of the panel. Controls layer thumbnail size (48–192 px). View state only.

---

## Right Bottom — Layer Properties

**Visibility rule:** Panel is visible **only when exactly one layer is selected**. Hidden when zero or multiple layers are selected.

**Fields (all editable via spinner + slider):**

| Field | Unit / Range |
|-------|-------------|
| offset x | px, relative to token center |
| offset y | px, relative to token center |
| rotation | degrees 0–360 |
| scale | float, 1.0 = native size |
| opacity | float 0–1 |
| hue | float 0–1 shift |
| saturation | float 0–1 (1 = unchanged) |
| brightness | float 0–1 (1 = unchanged) |

Each field edit fires a `SetLayerPropertyCommand`. Consecutive edits on the same field within the 500 ms merge window collapse into one history entry (prevents slider-drag spam).

---

## Bottom — Assets Library

**Layout:** Two-pane horizontal split.
- **Left pane:** Folder tree. Shows the merged virtual tree of `bundled://` and `user://` asset roots. Expandable nodes on demand.
- **Right pane:** Grid of image previews for the currently selected folder.

**Asset interaction:**
- Double-click asset → adds a new layer on top of the active token (centered, default props). Annotated: "Creates a new layer (on top) with chosen image."
- Drag asset onto canvas → same effect; drop position is ignored.

**Refresh content button:**
- Rescans the user-content root only (bundled is immutable per session).
- After rescan: "All images are refreshed each time the folder is refreshed." The folder tree and grid update to reflect any added/removed files.
- No automatic file-system watcher; refresh is always manual.

**Size slider:** Controls asset preview thumbnail size. View state only.

**User content path:** `<user_home>/.neuroshima-editor/content/` — created on first launch.

---

## Key Interaction Flows

### Adding a Layer to a Token

1. Select target token in Tokens Collection.
2. In Assets Library, navigate folder tree to desired asset.
3. Double-click asset (or drag to canvas).
4. New layer added at top of layer stack with default props.
5. Layer appears in Current Token Layers panel (top of list).
6. Action is on undo stack.

### Editing Layer Properties

1. Click one layer in Current Token Layers → Layer Properties panel becomes visible.
2. Adjust any field via spinner or slider.
3. Canvas re-renders live.
4. Each change fires a mergeable command onto the history stack.
5. Ctrl+Z undoes the last merged batch; Ctrl+Y redoes.

### Removing a Token

1. Hover over token thumbnail in Tokens Collection.
2. Trash button appears.
3. Click trash → confirmation dialog appears ("Are you sure?").
4. Confirm → token removed, command pushed to history stack.
5. Undo restores the token with all its layers.

### Saving

1. Ctrl+S → if path known, save silently; dirty marker (`*` in title) clears.
2. If path unknown, fall through to Save As flow.
3. Save As → file dialog → user picks path → save → path remembered for subsequent Ctrl+S.
4. Closing or loading with unsaved changes → "Save before closing?" dialog → Yes / No / Cancel.

---

## Noted Constraints and Invariants

- **No silent deletes on tokens** — confirmation always required.
- **No silent deletes on layers** — no confirmation required (undo covers it).
- **Layer properties hidden on multi-select** — prevents ambiguous edits; multi-select is for canvas tool operations and drag-reorder only.
- **Rename is always a command** — goes onto undo stack, not a direct model mutation.
- **Asset drag drop position ignored** — layer always added centered regardless of where it was dropped.
- **No file watcher** — asset library only updates on explicit refresh.
- **Token shape visible in thumbnail** — hex vs circle distinguishes UNIT vs MODIFIER without text labels.
- **Add-token buttons shaped like their token kind** — hex-shaped button for unit, round for modifier; visual affordance.
