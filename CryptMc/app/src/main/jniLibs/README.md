# CryptMc native libraries

Optional runtime libraries are ABI-specific and are intentionally not fabricated in source control.

For playit.gg tunneling, place the licensed agent library at:

- `arm64-v8a/libplayit_agent.so`
- `armeabi-v7a/libplayit_agent.so`
- `x86_64/libplayit_agent.so`

The app reports a missing-agent error instead of silently failing. Keep downloaded native binaries out of commits unless their redistribution license permits it.
