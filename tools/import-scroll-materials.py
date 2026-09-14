"""Import only material vectors from the owner's workbook; never edit the workbook.

Run with the bundled Python runtime (openpyxl). Existing recipes are untouched.
--check checks the resource without writing; otherwise mechanically regenerates it.
"""
import argparse
import hashlib
import json
from pathlib import Path

import openpyxl


def load_materials(source):
    workbook = openpyxl.load_workbook(source, read_only=True, data_only=True)
    try:
        sheet = workbook.worksheets[0]
        rows = list(sheet.values)
        headers = rows[0]
        expected = {"金", "木", "雷", "水", "土", "暗", "光", "火"}
        if set(headers[1:9]) != expected:
            raise ValueError("Unexpected element columns")
        names = set()
        materials = []
        for row_number, row in enumerate(rows[1:], 2):
            if row[0] is None:
                continue
            name = str(row[0]).strip()
            if not name or name in names:
                raise ValueError(f"Empty or duplicate material at row {row_number}")
            names.add(name)
            elements = {}
            for column in range(1, 9):
                value = row[column]
                if value is None:
                    continue
                if not isinstance(value, (int, float)) or value < 0 or int(value) != value:
                    raise ValueError(f"Invalid element amount at row {row_number}")
                if value:
                    elements[headers[column]] = int(value)
            if not elements:
                raise ValueError(f"Material without element data at row {row_number}")
            materials.append({"name": name, "elements": elements, "sortRank": len(materials)})
        return sheet.title, materials
    finally:
        workbook.close()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("workbook", type=Path)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    resource = Path(__file__).resolve().parents[1] / (
        "src/main/resources/assets/simmc_tool_set/scroll-data/game-data-v1.1.1.json"
    )
    sheet, materials = load_materials(args.workbook)
    data = json.loads(resource.read_text(encoding="utf-8"))
    if args.check:
        assert data["materials"] == materials, "Material resource differs from workbook"
    else:
        data["materials"] = materials
        # Material order follows the source; retain recipe/main-item names afterward.
        names = [m["name"] for m in materials]
        names.extend(n for n in data["nameSortRanks"] if n not in names)
        data["nameSortRanks"] = {name: i for i, name in enumerate(names)}
        data["materialSource"] = {
            "file": args.workbook.name,
            "sheet": sheet,
            "range": "A2:I127",
            "sha256": hashlib.sha256(args.workbook.read_bytes()).hexdigest(),
            "notes": "Blank element cells interpreted as zero; source labels retained without inferred aliases.",
        }
        resource.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"{sheet}: {len(materials)} materials; {'verified' if args.check else 'imported'}")


if __name__ == "__main__":
    main()
