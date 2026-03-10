# Changelog

## v0.15.0

- Restructure into multi-module Maven project (core + debug)
  - `eclipse.mcp.server` — core test runner plugin (all existing tools)
  - `eclipse.mcp.server.debugging` — debug tools add-on (skeleton, requires core)
- Each release now produces two JARs
- ToolRegistry is now extensible — external plugins can register additional tools
- Rename Java packages to `uk.l3si.eclipse.mcp`

## v0.14.0

- Add code coverage support via EclEmma/JaCoCo
  - New `coverage` parameter on `run_test` — launches tests in coverage mode
  - New `get_coverage` tool — returns detailed per-method and per-line coverage for a source class
  - Waits for coverage analysis if still loading (like `get_test_results` waits for running tests)

## v0.13.0

- Initial release
