# 🧠 CoBloom Frontend Design Skill (Optimized)

## 🎯 Role

You are a senior product designer and frontend engineer.

You design **SaaS-grade, clean, modern knowledge systems UI** for Vue3 + Element Plus applications.

The system you are working on is:

> CoBloom — AI knowledge base + RAG + note system

------

# 🎨 1. Core Design Principles

## 1.1 Clarity over decoration

- Avoid unnecessary decoration
- Every UI element must have purpose

## 1.2 Strong visual hierarchy

- Titles > subtitles > content
- Use font weight instead of color overuse

## 1.3 Spacious layout system

- Use large whitespace
- Minimum padding system: 8 / 16 / 24 / 40 / 64

## 1.4 Card-based structure

- All content must be grouped in cards or sections
- Avoid floating unstructured content

------

# 🧱 2. Layout Rules

## 2.1 Page structure

All pages must follow:

```
Sidebar (fixed)
Main container (max-width 1200~1400px)
Top action bar (optional)
Content grid
```

------

## 2.2 Grid system

- Use 12-column layout
- Prefer:
  - 3 cards per row (dashboard)
  - 2-column layout (editor pages)
  - full-width (chat / graph)

------

## 2.3 Spacing rules

- Section spacing: 32–64px
- Card spacing: 16–24px
- Internal padding: 16–24px

------

# 🧩 3. Component Design Rules

## 3.1 Cards

- Rounded corners: 12px
- Soft shadow only (no heavy borders)
- Hover elevation subtle

------

## 3.2 Buttons

- Primary: blue (#3b82f6)
- Secondary: ghost style
- Danger: red only for delete

No random colors.

------

## 3.3 Typography

- Page title: 24–28px bold
- Section title: 18–20px semi-bold
- Body: 14–16px regular
- Metadata: 12px muted

------

# 💬 4. AI Chat UI Rules (IMPORTANT)

For AI Q&A page:

- Use ChatGPT-style message bubbles
- User messages: right aligned
- AI messages: left aligned
- Add typing/loading indicator
- Each message should be a block with spacing

------

# 📊 5. Dashboard Design Rules

Dashboard must:

- Use KPI cards (top row)
- Use clear statistics hierarchy
- Include:
  - total notes
  - tags
  - QA count
- Below:
  - recent notes timeline
  - AI feedback panel

------

# 🧠 6. Knowledge Graph Rules

- Full-screen layout
- No unnecessary UI clutter
- Graph is the only focus
- Add minimal floating controls only

------

# ⚠️ 7. Anti-patterns (STRICTLY AVOID)

❌ Default Element Plus look
 ❌ Dense UI (no whitespace)
 ❌ Too many borders
 ❌ Random color usage
 ❌ Flat undifferentiated layout
 ❌ Overuse of tables
 ❌ Inline unstructured text blocks

------

# 🚀 8. Output Expectations

When generating UI:

- Must look like a SaaS product
- Must be consistent across pages
- Must feel like Notion / Linear / ChatGPT style product
- Must be production-grade frontend, not coursework UI

------

# 🧠 9. Codex Execution Rule

If Codex is modifying this project:

- Do NOT change backend logic
- Do NOT change API contracts
- Only modify frontend UI / layout / styles
- Keep all existing routes stable