# 生成图标记录

工具：内置 `image_gen.imagegen`；未使用 CLI/API fallback。2026-10-23。两张图均要求真实透明 alpha，已检查最终 PNG 含透明通道。

- `brand-icon.png`：原始结果为 1280×1280，预览资源缩至 512×512。原图保存在 Codex generated_images，不改动原图。
- `live-signal.png`：原始结果为 1280×1280，预览资源缩至 128×128。
- 仅做显示尺寸优化，不改变生成内容。正式 Android 接入时按 dp 与资源密度控制解码尺寸，并只缓存一次。

## Brand icon prompt

Use case: logo-brand. Asset type: original ZTV Android TV app brand icon, used at 44dp in the programme menu and 84dp on the loading screen. Primary request: a beautifully crafted, highly legible original Z monogram, shaped from two gently folded continuous ribbons, evoking broadcast light and a flowing television signal. Preserve the recognisable letter Z of the existing app identity. Palette: warm ivory #F4F1E8 and muted champagne #DDB477, subtle charcoal edge #141615. Style: restrained industrial design, satin ceramic and softly brushed champagne metal, refined shallow relief with very little depth, precise silhouette, front view. Balanced square composition, symbol occupies 78 percent of the canvas, generous transparent margin, no tile or background. Soft studio lighting, no neon, no glow, no drop shadow beyond the object, no fine details that disappear at TV icon size. Real alpha transparent background. No text, no watermark, no play triangle, no official broadcaster logos.

## Live signal prompt

Use case: logo-brand. Asset type: a premium LIVE BROADCAST status icon for a charcoal and warm-ivory Android TV programme guide. Primary request: one single iconic broadcast symbol, a solid central circle with one pair of balanced short signal arcs on each side. Exceptionally clean silhouette, optically balanced, legible even at 24dp. Front view, very shallow soft relief in satin muted champagne metal #DDB477 with tiny warm ivory #F4F1E8 highlights. Quiet, refined home-cinema industrial design, no toy-like rounded inflated shapes, no glossy plastic, no neon, no glow. Shape fills 76 percent of a square canvas with generous transparent margin. Real transparent alpha background. No background tile, no letters, no wording, no extra symbols, no watermark.
