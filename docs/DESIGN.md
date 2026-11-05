# Liquid Silver design

Reference: Apple HIG Materials, Adopting Liquid Glass, and WWDC25 Meet Liquid Glass.

- https://developer.apple.com/design/human-interface-guidelines/materials
- https://developer.apple.com/documentation/technologyoverviews/adopting-liquid-glass
- https://developer.apple.com/videos/play/wwdc2025/219/

Navigation and controls form a floating functional layer. Evidence is on stable, legible surfaces. Avoid glass-on-glass and transparency behind long reading content. Blue indicates actions; verdict colors always have text labels. The Android implementation is inspired by the principles, not Apple's private/system rendering.

The navigation samples an isolated content GraphicsLayer, crops it to local coordinates and blurs on supported Android versions; a tint and directional rim maintain contrast. API <31 and reduced transparency use an opaque fallback. External-app overlays use a fixed, legible material. They cannot rely on sampling protected or other-app content. System light/dark mode, text scaling, inset handling and a reduced-motion option are supported.

Layout: 22dp page margins, 18dp section gaps, 22–30dp corner radii, 48dp+ principal actions. Long results scroll independently; navigation and keyboard space are reserved. Every icon-only action has a label for accessibility. Source snippets are described as model synthesis, not publisher quotations.
