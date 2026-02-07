#!/usr/bin/env python3
"""
Script to check for missing translations in localized strings.xml files.
Compares the primary English strings.xml with all localized versions.
"""

import xml.etree.ElementTree as ET
import sys
from pathlib import Path
from typing import Dict, Set, List
import json


def parse_strings_xml(file_path: Path) -> Dict[str, str]:
    """Parse strings.xml and return a dictionary of string names to values."""
    if not file_path.exists():
        return {}
    
    tree = ET.parse(file_path)
    root = tree.getroot()
    
    strings = {}
    for string_elem in root.findall('string'):
        name = string_elem.get('name')
        if name:
            # Get text content, handling None
            text = string_elem.text or ""
            strings[name] = text
    
    return strings


def find_missing_strings(primary: Dict[str, str], localized: Dict[str, str]) -> Dict[str, str]:
    """Find strings present in primary but missing in localized."""
    primary_keys = set(primary.keys())
    localized_keys = set(localized.keys())
    
    missing_keys = primary_keys - localized_keys
    
    return {key: primary[key] for key in sorted(missing_keys)}


def generate_missing_strings_xml(missing: Dict[str, str]) -> str:
    """Generate XML snippet for missing strings."""
    if not missing:
        return ""
    
    # Create a root element
    root = ET.Element("resources")
    for name, value in missing.items():
        string_elem = ET.SubElement(root, "string")
        string_elem.set("name", name)
        string_elem.text = value
    
    # Convert to string with proper formatting
    ET.indent(root, space='    ')
    return ET.tostring(root, encoding='unicode', method='xml')


def main():
    # Define paths
    res_dir = Path("app/src/main/res")
    primary_strings = res_dir / "values" / "strings.xml"
    
    # Supported locales based on the custom instructions
    locales = ["de", "es", "fr", "it"]
    
    # Parse primary strings
    print("📖 Parsing primary strings.xml (English)...")
    primary = parse_strings_xml(primary_strings)
    print(f"   Found {len(primary)} strings in primary file")
    
    # Check each locale
    results = {}
    has_missing = False
    
    for locale in locales:
        locale_strings = res_dir / f"values-{locale}" / "strings.xml"
        print(f"\n🔍 Checking locale: {locale.upper()}...")
        
        localized = parse_strings_xml(locale_strings)
        print(f"   Found {len(localized)} strings in {locale} file")
        
        missing = find_missing_strings(primary, localized)
        
        if missing:
            has_missing = True
            print(f"   ⚠️  Missing {len(missing)} strings:")
            for key in list(missing.keys())[:5]:  # Show first 5
                print(f"      - {key}")
            if len(missing) > 5:
                print(f"      ... and {len(missing) - 5} more")
            
            results[locale] = {
                "missing_count": len(missing),
                "missing_strings": missing,
                "xml_snippet": generate_missing_strings_xml(missing)
            }
        else:
            print(f"   ✅ All strings present!")
            results[locale] = {
                "missing_count": 0,
                "missing_strings": {},
                "xml_snippet": ""
            }
    
    # Write results to JSON for GitHub Actions to consume
    output_file = Path("localization_check_results.json")
    with open(output_file, "w", encoding="utf-8") as f:
        json.dump(results, f, indent=2, ensure_ascii=False)
    
    print(f"\n📊 Results written to {output_file}")
    
    # Exit with error code if any locale has missing strings
    if has_missing:
        print("\n❌ Some locales have missing strings!")
        sys.exit(1)
    else:
        print("\n✅ All locales are complete!")
        sys.exit(0)


if __name__ == "__main__":
    main()
