Read "DOCS_SCRIPTING.md" first and inspect the existing implementation before making changes.

Goal

Add a universal execution system to RoCat that supports two script modes:

1. Template Mode — Existing "onLaunch()", "RoCatUI.*", and "RoCat.render()" scripts.
2. Exec Mode — Normal Node.js-style scripts using "require", async functions, promises, "console.log", and module exports.

Scripts should be automatically detected and executed in the appropriate mode without breaking existing scripts.

Requirements

1. Automatic Mode Detection

- Detect template scripts through "onLaunch()", "RoCatUI.*", "RoCat.render()", or "RoCatDOM.*".
- Treat other scripts as Exec Mode by default.
- Support explicit overrides using "// @mode exec" or "// @mode template".
- Detect CommonJS and ESM syntax automatically.

2. Exec Tab

Create a dedicated Exec tab that:

- Lists all detected Exec Mode scripts.
- Provides a Run button for each script.
- Displays console output, errors, and return values in a readable log panel.
- Supports script arguments through an input field.
- Includes searchable input history with the newest entries first.

3. Node Compatibility on Rhino 1.7.15

Rhino does not natively support modern Node.js syntax, so implement a lightweight compatibility layer.

- Adapt CommonJS and ESM syntax where reasonably possible.
- Support async/await through compatible wrappers or transpilation.
- Provide minimal shims for "require('crypto')", "require('https')", "URL", and "Buffer".
- Map crypto operations and HTTPS requests to existing Android bridges such as OkHttp/fetch.
- Do not implement a full Node.js runtime or bundler.
- Unsupported scripts must fail gracefully with clear error messages, never crash the app.

4. Template Mode Improvements

Keep existing template behavior fully backward-compatible.

Add configurable canvas layouts:

- Custom header title, subtitle, and action buttons.
- Custom footer content and buttons.
- Button placement: "header", "body" (default), or "footer".
- Add "RoCatUI.setHeader()" and "RoCatUI.setFooter()" APIs.
- Support a "placement" property in button descriptors.

Existing scripts without layout configuration must retain their current appearance.

5. Execution Safety

- Apply the existing 10M-instruction watchdog to both modes.
- Preserve the existing anti-crash mechanisms.
- Ensure execution errors are isolated from the rest of the application.

6. Documentation & Examples

Update "DOCS_SCRIPTING.md" with documentation covering:

- Template and Exec modes.
- Automatic detection and "@mode" overrides.
- CommonJS/ESM compatibility.
- Available Node.js shims.
- Exec tab usage and input history.
- Header, footer, and button placement APIs.

Include:

- One working Exec Mode example based on the "rudder.chat" script.
- One Template Mode example using the new header/footer configuration.

Deliverables

1. Automatic execution-mode detection.
2. Dedicated Exec tab with script execution and input history.
3. Lightweight CommonJS/ESM compatibility layer.
4. Node module shims backed by existing Android bridges.
5. Configurable template header, footer, and button placement.
6. Updated documentation and working examples.

Important: Inspect the existing architecture first. Reuse existing bridges and components wherever possible. Keep changes focused, avoid unnecessary refactoring, and do not break existing template scripts.