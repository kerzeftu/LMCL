# Java Code Style Requirements

These rules apply to all Java code written or modified in this repository.

## Nullability

- Annotate every class with JetBrains Annotations `@NotNullByDefault`.
- Any type, field, parameter, return value, local variable, or generic type argument that may be `null` must be explicitly annotated with `@Nullable`.
- Nullability must never be implicit.

## Immutability

- Immutable arrays and collections must be explicitly annotated with JetBrains Annotations `@Unmodifiable` or `@UnmodifiableView` as appropriate.
- For arrays, use type-use syntax such as `String @Unmodifiable []`.

## Documentation

- Every class, field, and method must have documentation.
- Documentation must use `///` Markdown-style Javadoc comments.
- Keep documentation accurate and specific to the actual behavior, constraints, and side effects.
- Add concise implementation comments inside complex logic whenever they materially improve readability or explain non-obvious behavior.

## Documentation First

- Project development is paused. Read `docs-community/DOCS.md` for the current status and documentation index.
- Before changing code, consult the relevant troubleshooting, architecture, user-intent, publishing or maintenance guide in `docs-community/`. Do not review source first if the guides answer the question.
- When behavior changes, update the affected current guidance in the same commit. Preserve historical steps and reviews as records rather than presenting them as current plans.

## Verification Discipline

- Do not over-defend or over-verify. Follow the documented standard once (see `PUBLISHING.md` §5), confirm the change works, and stop.
- Do not add repeated verification scripts, piles of asserts, or multiple cross-check rounds for the same change.
- Visual/UX claims require real Windows evidence; WSL2 code-only checks are not acceptance.
