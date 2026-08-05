from pathlib import Path
p = Path(r"c:\Users\j\Desktop\nissan gtr\apps\ios\GTRCustomer.xcodeproj\project.pbxproj")
t = p.read_text(encoding="utf-8")
bref = "A10000000000000000000096"
bfile = "A10000000000000000000097"
if "CategoriesGridScreen.swift" in t:
    print("already")
else:
    t = t.replace(
        "A10000000000000000000094 /* CustomerShellChrome.swift in Sources */ = {isa = PBXBuildFile; fileRef = A10000000000000000000095 /* CustomerShellChrome.swift */; };",
        "A10000000000000000000094 /* CustomerShellChrome.swift in Sources */ = {isa = PBXBuildFile; fileRef = A10000000000000000000095 /* CustomerShellChrome.swift */; };\n\t\t"
        + bref
        + " /* CategoriesGridScreen.swift in Sources */ = {isa = PBXBuildFile; fileRef = "
        + bfile
        + " /* CategoriesGridScreen.swift */; };",
    )
    t = t.replace(
        'A10000000000000000000095 /* CustomerShellChrome.swift */ = {isa = PBXFileReference; lastKnownFileType = sourcecode.swift; path = CustomerShellChrome.swift; sourceTree = "<group>"; };',
        'A10000000000000000000095 /* CustomerShellChrome.swift */ = {isa = PBXFileReference; lastKnownFileType = sourcecode.swift; path = CustomerShellChrome.swift; sourceTree = "<group>"; };\n\t\t'
        + bfile
        + ' /* CategoriesGridScreen.swift */ = {isa = PBXFileReference; lastKnownFileType = sourcecode.swift; path = CategoriesGridScreen.swift; sourceTree = "<group>"; };',
    )
    t = t.replace(
        "A10000000000000000000095 /* CustomerShellChrome.swift */,",
        "A10000000000000000000095 /* CustomerShellChrome.swift */,\n\t\t\t\t"
        + bfile
        + " /* CategoriesGridScreen.swift */,",
    )
    t = t.replace(
        "A10000000000000000000094 /* CustomerShellChrome.swift in Sources */,",
        "A10000000000000000000094 /* CustomerShellChrome.swift in Sources */,\n\t\t\t\t"
        + bref
        + " /* CategoriesGridScreen.swift in Sources */,",
    )
    p.write_text(t, encoding="utf-8")
    print("added")
