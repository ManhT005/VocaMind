"""Fail closed unless the device evidence proves the offline precondition."""
from pathlib import Path
import re
import sys


def require_offline(airplane, wifi, connectivity):
    if airplane.strip() != '1':
        raise ValueError('Airplane mode must be 1')
    if wifi.strip() != '0':
        raise ValueError('Wi-Fi must be disabled (0)')
    defaults = re.findall(r'^\s*Active default network:\s*(\S+)\s*$', connectivity, re.MULTILINE)
    if defaults != ['none']:
        raise ValueError('Expected exactly one active-default-network entry with value none')


def read(path):
    data = path.read_bytes()
    return data.decode('utf-16' if data.startswith((b'\xff\xfe', b'\xfe\xff')) else 'utf-8-sig')


if __name__ == '__main__':
    directory = Path(sys.argv[1])
    try:
        require_offline(read(directory / 'airplane-mode.txt'), read(directory / 'wifi-state.txt'),
                        read(directory / 'connectivity-before-launch.txt'))
    except (OSError, UnicodeError, ValueError) as failure:
        print(f'Offline precondition FAILED: {failure}', file=sys.stderr)
        sys.exit(1)
    print('Offline precondition PASS: airplane=1, wifi=0, active default network=none')
