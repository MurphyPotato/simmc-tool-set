"""Read-only distribution audit, complementary to tests (not a feature test)."""
import argparse
import hashlib
import json
import zipfile
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument("jar", type=Path)
args = parser.parse_args()
with zipfile.ZipFile(args.jar) as jar:
    names = jar.namelist()
    assert len(names) == len(set(names)), "Duplicate ZIP entries"
    assert jar.testzip() is None, "ZIP checksum failure"
    metadata = json.loads(jar.read("fabric.mod.json"))
    assert metadata["version"] == "1.1.9-dev.20260915.1"
    assert metadata["environment"] == "client"
    assert metadata["id"] == "simmc_tool_set"
    assert metadata["depends"]["minecraft"] == "1.21.11"
    assert metadata["depends"]["fabricloader"] == ">=0.19.5"
    assert metadata["depends"]["fabric-api"] == ">=0.141.6+1.21.11"
    assert metadata["entrypoints"]["client"] == [
        "com.murphypotato.simmctoolset.client.ToolSetClient"
    ]
    for mixin in metadata["mixins"]:
        assert mixin in names
        json.loads(jar.read(mixin))
    assert not any(n.startswith(("net/minecraft/", "net/fabricmc/", "org/junit/"))
                   or "/smoke/" in n or n.endswith("Test.class") for n in names)
    for cls in ("domain/MaterialDecay", "domain/DecayPlanner", "config/ScrollUsageStore",
                "client/CalculatorScreen", "client/ScrollUsageScreen"):
        assert f"com/murphypotato/simmctoolset/internal/scroll/{cls}.class" in names, (
            f"Missing required class: {cls}"
        )
    data = json.loads(jar.read("assets/simmc_tool_set/scroll-data/game-data-v1.1.1.json"))
    assert len(data["materials"]) == 126
    assert len(data["recipes"]) == 20
    assert data["materialSource"]["sha256"] == (
        "760abdd87a4e862e3a553e90c571c3201412a05cf2181b647d4486c633a6d9b5"
    )
digest = hashlib.sha256(args.jar.read_bytes()).hexdigest().upper()
print(json.dumps({"jar": str(args.jar), "bytes": args.jar.stat().st_size,
                  "sha256": digest, "staticAudit": "passed"}, ensure_ascii=False))
