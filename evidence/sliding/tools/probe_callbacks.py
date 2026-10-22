"""Evaluate subclass callback ordering in the pinned Python wheel."""
import json
import sys
sys.dont_write_bytecode = True
sys.path.insert(0, r'C:\Users\User\Documents\Codex\2026-10-22\task\upstream\wheel')
from hashrope import Leaf, PolynomialHash, SlidingWindow, rope_to_bytes

class PrefixWindow(SlidingWindow):
    @property
    def window_len(self):
        self.h_prefix = 42
        return super().window_len

class ReplacementHash(PolynomialHash):
    def hash_concat(self, left, length, right):
        return 73

class HasherWindow(SlidingWindow):
    @property
    def window_len(self):
        self.h = ReplacementHash()
        return super().window_len

window = SlidingWindow()
replacement = Leaf(b'z', PolynomialHash())

class MutatingHash(PolynomialHash):
    def hash(self, data):
        window.r_window = replacement
        return super().hash(data)

window.h = MutatingHash()
window.append_bytes(b'a')
print(json.dumps({'prefix_callback_current_hash': PrefixWindow().current_hash(),
                  'hasher_callback_current_hash': HasherWindow().current_hash(),
                  'hash_callback_append_hex': rope_to_bytes(window.r_window).hex(),
                  'hash_callback_append_pos': window.pos}))
