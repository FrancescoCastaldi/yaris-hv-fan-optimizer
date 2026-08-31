import unittest

def clean_response(raw: str) -> str:
    return (raw.replace(>, ")
 .replace(\r, )
 .replace(\n, )
 .replace( , )
 .replace(SEARCHING..., )
 .replace(BUSINIT:OK, )
 .replace(BUSINIT:..., )
 .strip())

def is_error(response: str) -> bool:
 clean = clean_response(response).upper()
 return any(err in clean for err in [NODATA, ERROR, UNABLETOCONNECT, STOPPED, TIMEOUT, CANERROR, FBERROR])

def parse_battery_response(raw: str, is_forced: bool):
 clean = clean_response(raw).upper()
 if is_error(clean) or len(clean) < 8:
 return None
 
 hex_payload = clean
 if 6228C1 in hex_payload:
 hex_payload = hex_payload[hex_payload.index(6228C1) + 6:]
 elif 6161 in hex_payload:
 hex_payload = hex_payload[hex_payload.index(6161) + 4:]
 
 if len(hex_payload) < 8:
 return None
 
 t1 = int(hex_payload[0:2], 16) - 40
 t2 = int(hex_payload[2:4], 16) - 40 if len(hex_payload) >= 4 else t1
 t3 = int(hex_payload[4:6], 16) - 40 if len(hex_payload) >= 6 else t1
 t4 = int(hex_payload[6:8], 16) - 40 if len(hex_payload) >= 8 else t1
 intake = int(hex_payload[8:10], 16) - 40 if len(hex_payload) >= 10 else t1
 fan_level = min(6, max(0, int(hex_payload[10:12], 16))) if len(hex_payload) >= 12 else (6 if is_forced else 0)
 
 temps = [t for t in [t1, t2, t3, t4] if -30 < t < 100]
 max_t = max(temps) if temps else t1
 
 return {
 t1: t1, t2: t2, t3: t3, t4: t4,
 max_t: max_t, intake: intake, fan_level: fan_level,
 is_forced: is_forced
 }

class TestToyotaHvLogic(unittest.TestCase):
 def test_clean_response(self):
 raw = 41 00 BE 1F B8 10 \r\r>
 self.assertEqual(clean_response(raw), 4100BE1FB810)

 def test_parse_battery_frame(self):
 raw = 62 28 C1 40 42 41 3F 3E 06 >
 res = parse_battery_response(raw, is_forced=True)
 self.assertIsNotNone(res)
 self.assertEqual(res[t1], 24)
 self.assertEqual(res[t2], 26)
 self.assertEqual(res[t3], 25)
 self.assertEqual(res[t4], 23)
 self.assertEqual(res[max_t], 26)
 self.assertEqual(res[intake], 22)
 self.assertEqual(res[fan_level], 6)

 def test_errors(self):
 self.assertTrue(is_error(NO DATA\r>))
 self.assertIsNone(parse_battery_response(NO DATA\r>, is_forced=False))

if __name__ == '__main__':
 unittest.main()
