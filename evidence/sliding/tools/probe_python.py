"""Read-only probes of the pinned extracted distribution, never the original tree."""
import json
import sys
sys.dont_write_bytecode = True
sys.path.insert(0, r'C:\Users\User\Documents\Codex\2026-10-22\task\upstream\wheel')
from hashrope import SlidingWindow, rope_to_bytes

for offset, length in [(0, 0), (0, 1), (-1, 3), (-1, -2), (-2, -1), (2, -1), (4, 0), (1, 0)]:
    window = SlidingWindow(3, 2)
    window.append_bytes(b'abc')
    try:
        window.append_copy(offset, length)
        result = {'pos': window.pos, 'prefix': window.l_prefix, 'window': rope_to_bytes(window.r_window).hex(), 'hash': window.current_hash()}
    except Exception as exc:
        result = {'error': type(exc).__name__, 'message': str(exc), 'pos': window.pos, 'window': rope_to_bytes(window.r_window).hex()}
    print(json.dumps({'offset': offset, 'length': length, 'result': result}))
window = SlidingWindow()
try:
    window.append_bytes(None)
    print(json.dumps({'appendNone': 'no-op', 'pos': window.pos}))
except Exception as exc:
    print(json.dumps({'appendNone': type(exc).__name__}))
