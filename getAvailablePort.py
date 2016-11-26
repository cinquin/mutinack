#!/usr/bin/env python
import socket;
import sys;
s=socket.socket();
try:
    s.bind(("", int(sys.argv[1])));
except:
    s.bind(("", 0));
print(s.getsockname()[1]);
s.close()
