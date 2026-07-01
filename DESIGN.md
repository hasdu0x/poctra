# FastAuth — Sign up with email. Own a real wallet.

## Mission
Create implementation-ready, token-driven UI guidance for FastAuth — Sign up with email. Own a real wallet. that is optimized for consistency, accessibility, and fast delivery across content site.

## Brand
- Product/brand: FastAuth — Sign up with email. Own a real wallet.
- URL: https://auth.near.org/
- Audience: readers and knowledge seekers
- Product surface: content site

## Style Foundations
- Visual style: clean, functional, implementation-oriented
- Main font style: `font.family.primary=-apple-system`, `font.family.stack=-apple-system, BlinkMacSystemFont, SF Pro Text, SF Pro Display, Segoe UI, system-ui, sans-serif`, `font.size.base=16px`, `font.weight.base=400`, `font.lineHeight.base=normal`
- Typography scale: `font.size.xs=11px`, `font.size.sm=12px`, `font.size.md=13px`, `font.size.lg=14px`, `font.size.xl=15px`, `font.size.2xl=16px`, `font.size.3xl=17px`, `font.size.4xl=18px`
- Color palette: `color.surface.base=#000000`, `color.text.secondary=#757571`, `color.text.tertiary=#ffffff`, `color.surface.raised=#00f29b`, `color.surface.strong=#f5f4ed`
- Spacing scale: `space.1=2px`, `space.2=4px`, `space.3=6px`, `space.4=8px`, `space.5=10px`, `space.6=12px`, `space.7=16px`, `space.8=20px`
- Radius/shadow/motion tokens: `radius.xs=8px`, `radius.sm=12px`, `radius.md=16px`, `radius.lg=100px` | `shadow.1=rgba(0, 0, 0, 0.08) 0px 0px 8px 0px` | `motion.duration.instant=120ms`, `motion.duration.fast=160ms`

## Accessibility
- Target: WCAG 2.2 AA
- Keyboard-first interactions required.
- Focus-visible rules required.
- Contrast constraints required.

## Writing Tone
Concise, confident, implementation-focused.

## Rules: Do
- Use semantic tokens, not raw hex values, in component guidance.
- Every component must define states for default, hover, focus-visible, active, disabled, loading, and error.
- Component behavior should specify responsive and edge-case handling.
- Interactive components must document keyboard, pointer, and touch behavior.
- Accessibility acceptance criteria must be testable in implementation.

## Rules: Don't
- Do not allow low-contrast text or hidden focus indicators.
- Do not introduce one-off spacing or typography exceptions.
- Do not use ambiguous labels or non-descriptive actions.
- Do not ship component guidance without explicit state rules.

## Guideline Authoring Workflow
1. Restate design intent in one sentence.
2. Define foundations and semantic tokens.
3. Define component anatomy, variants, interactions, and state behavior.
4. Add accessibility acceptance criteria with pass/fail checks.
5. Add anti-patterns, migration notes, and edge-case handling.
6. End with a QA checklist.

## Required Output Structure
- Context and goals.
- Design tokens and foundations.
- Component-level rules (anatomy, variants, states, responsive behavior).
- Accessibility requirements and testable acceptance criteria.
- Content and tone standards with examples.
- Anti-patterns and prohibited implementations.
- QA checklist.

## Component Rule Expectations
- Include keyboard, pointer, and touch behavior.
- Include spacing and typography token requirements.
- Include long-content, overflow, and empty-state handling.
- Include known page component density: links (26), buttons (16), cards (10), lists (4), navigation (2).

- Extraction diagnostics: Audience and product surface inference confidence is low; verify generated brand context.

## Quality Gates
- Every non-negotiable rule must use "must".
- Every recommendation should use "should".
- Every accessibility rule must be testable in implementation.
- Teams should prefer system consistency over local visual exceptions.
