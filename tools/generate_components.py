#!/usr/bin/env python3
"""
Derives one Spring configuration class per upstream component from the eGovFrame common
components source tree, plus the aggregator that imports them and a Markdown catalog.

Usage:
    python3 -I tools/generate_components.py [--upstream DIR] [--out DIR] [--doc FILE]

What a "component" is here: a package under egovframework/com that has a service/impl
subpackage (cmm, sym/log/clg, cop/smt/djm, ...). Every stereotype class in that package subtree,
except what belongs to a nested component or lives in a .web. package, is the component's own.

Edges followed (see README, "The taxonomy"):
  0. class -> type, through constructor parameters (explicit, or Lombok @RequiredArgsConstructor
     final fields) and @Autowired fields, resolved to the stereotype class of that name or
     implementing that interface;
  1. class -> bean name, through @Resource(name = "...") on fields and setters;
     a name resolves to a stereotype class (its explicit @Service/@Repository/@Component name),
     to an ID-generation XML (context-idgn-*.xml) defining a bean of that name,
     to a bean the core auto-configuration provides, or to nothing (listed as external);
  2. component -> mapper directories, by package path under egovframework/mapper/com.

A component's configuration imports the transitive closure of edge 1, contributes the mapper
directories of every component the closure touches, and registers the ID-generation XMLs the
closure references. Duplicates between components are harmless: @Import dedups by class, mapper
directories are a set, and the XML registrar skips bean names already present.
"""
import argparse
import os
import re
import sys
from collections import defaultdict, deque

STEREOTYPES = ("Service", "Repository", "Component", "Configuration")
EXCLUDED_STEREOTYPES = ("Controller", "RestController", "ControllerAdvice")

# Bean names the core auto-configuration (EgovFrameAutoConfiguration) provides.
CORE_BEANS = {
    "egov.dataSource", "egov.sqlSession", "egov.sqlSessionTemplate", "egov.lobHandler",
    "messageSource", "egovMessageSource", "leaveaTrace", "propertiesService", "txManager",
    "egovUserDetailsHelper", "egovLoginConfig",
    "egovPageLinkWhitelist", "egovRSSWhitelist", "egovNextUrlWhitelist",
}

# Classes never imported: they need beans that exist only in upstream's deployment XML
# (schedulers, mail senders, monitoring triggers), or an implementation that is not shipped.
EXCLUDED_CLASS_PATTERNS = [
    re.compile(r"\.web\."),                  # controllers and their advices
    re.compile(r"Scheduling$"),              # need *Trigger beans from context-scheduling-*.xml
    re.compile(r"\.uss\.ion\.tir\."),        # Twitter: EgovXClient has no implementation shipped
    re.compile(r"\.cmm\.util\.EgovPasswordResolver$"),  # needs egovEnvCryptoService; used by controllers only
]

# Components whose classes need a library that is not on the classpath by default; listed with the
# external requirements so a consumer knows what to add.
EXTERNAL_LIBRARIES = {
    "cop/sms": "library project:smeapi:2.7.0 (proprietary SMS gateway client bundled in upstream's WEB-INF/lib; "
               "an optional dependency of egovframe-common-components)",
}

# Components never generated, with the reason shown in the catalog.
EXCLUDED_COMPONENTS = {
    "uss/ion/tir": "Twitter integration; EgovXClient has no implementation in the distribution",
}

ANNOTATION_RE = re.compile(
    r'^\s*@(' + "|".join(STEREOTYPES + EXCLUDED_STEREOTYPES) + r')\b(?:\s*\(\s*(?:value\s*=\s*)?"([^"]*)"\s*\))?',
    re.M)
RESOURCE_RE = re.compile(r'@Resource\s*\(\s*name\s*=\s*"([^"]+)"')
PACKAGE_RE = re.compile(r'^\s*package\s+([\w.]+)\s*;', re.M)
IMPLEMENTS_RE = re.compile(r'\bclass\s+\w+[^{]*?\bimplements\s+([\w.,\s<>]+?)\s*\{', re.S)
AUTOWIRED_RE = re.compile(r'@Autowired\s*(?:\([^)]*\))?\s*(?:private|protected|public)?\s*(?:final\s+)?([\w.]+)(?:<[^>]*>)?\s+\w+\s*;')
CLASS_RE = re.compile(r'^\s*public\s+(?:abstract\s+)?(?:final\s+)?(?:class|interface|enum)\s+(\w+)', re.M)
XML_BEAN_RE = re.compile(r'<bean[^>]*\s(?:id|name)="([^"]+)"')
BEAN_METHOD_RE = re.compile(r'@Bean\b[^\n]*\n\s*(?:public\s+)?[\w.<>\[\]]+\s+(\w+)\s*\(', re.M)


def strip_comments(src):
    src = re.sub(r'/\*.*?\*/', '', src, flags=re.S)
    src = re.sub(r'//[^\n]*', '', src)
    return src


class JavaClass:
    def __init__(self, path, rel, src):
        self.path = path
        self.rel = rel                       # path relative to egovframework/com, e.g. sym/log/clg/service/impl/LoginLogDAO.java
        self.package = PACKAGE_RE.search(src).group(1)
        m = CLASS_RE.search(src)
        self.simple = m.group(1) if m else os.path.basename(path)[:-5]
        self.fqcn = f"{self.package}.{self.simple}"
        self.stereotype = None
        self.bean_name = None
        for m in ANNOTATION_RE.finditer(src):
            ann, name = m.group(1), m.group(2)
            if ann in EXCLUDED_STEREOTYPES:
                self.stereotype = None
                self.bean_name = None
                self.excluded = True
                break
            self.stereotype = ann
            self.bean_name = name or None
        self.resources = sorted(set(RESOURCE_RE.findall(src)))
        # by-type edges (README edge 7): constructor parameters and @Autowired fields
        self.types = set(AUTOWIRED_RE.findall(src))
        for params in re.findall(r'\bpublic\s+' + re.escape(self.simple) + r'\s*\(([^)]*)\)', src):
            for param in params.split(","):
                param = param.strip()
                if param:
                    self.types.add(re.sub(r'<.*>', '', param.split()[-2] if len(param.split()) > 1 else param).strip())
        # Lombok-generated constructor: every final field is a constructor parameter
        if re.search(r'^\s*@(RequiredArgsConstructor|AllArgsConstructor)\b', src, re.M):
            for t in re.findall(r'^\s*private\s+(?:static\s+)?final\s+([\w.]+)(?:<[^>]*>)?\s+\w+\s*;', src, re.M):
                self.types.add(t)
        m = IMPLEMENTS_RE.search(src)
        self.implements = [re.sub(r'<.*>', '', t).strip().split(".")[-1] for t in m.group(1).split(",")] if m else []
        self.bean_methods = sorted(set(BEAN_METHOD_RE.findall(src))) if self.stereotype == "Configuration" else []

    @property
    def importable(self):
        if self.stereotype is None:
            return False
        return not any(p.search(self.fqcn) for p in EXCLUDED_CLASS_PATTERNS)


def scan_java(java_root):
    classes = []
    for root, _, files in os.walk(java_root):
        for f in files:
            if not f.endswith(".java"):
                continue
            p = os.path.join(root, f)
            src = strip_comments(open(p, encoding="utf-8", errors="replace").read())
            if not PACKAGE_RE.search(src):
                continue
            c = JavaClass(p, os.path.relpath(p, java_root), src)
            c.excluded = getattr(c, "excluded", False)
            classes.append(c)
    return classes


def scan_idgn(spring_root):
    """bean name -> xml file name (context-idgn-*.xml)"""
    out = {}
    d = os.path.join(spring_root, "idgn")
    for f in sorted(os.listdir(d)):
        if not f.endswith(".xml"):
            continue
        src = open(os.path.join(d, f), encoding="utf-8", errors="replace").read()
        src = re.sub(r'<!--.*?-->', '', src, flags=re.S)
        for name in XML_BEAN_RE.findall(src):
            out.setdefault(name, f)
    return out


def find_components(java_root):
    comps = []
    for root, dirs, _ in os.walk(java_root):
        rel = os.path.relpath(root, java_root)
        if os.path.isdir(os.path.join(root, "service", "impl")):
            comps.append(rel.replace(os.sep, "/"))
    return sorted(comps)


def owner_of(rel_path, components):
    """nearest component whose path is a prefix of the class's directory"""
    d = os.path.dirname(rel_path).replace(os.sep, "/")
    best = None
    for c in components:
        if d == c or d.startswith(c + "/"):
            if best is None or len(c) > len(best):
                best = c
    return best


def mapper_dirs(mapper_root, components):
    """component -> sorted list of mapper directories (relative to mapper/com) it owns"""
    owned = defaultdict(set)
    for root, _, files in os.walk(mapper_root):
        if not any(f.endswith(".xml") for f in files):
            continue
        rel = os.path.relpath(root, mapper_root).replace(os.sep, "/")
        best = None
        for c in components:
            if rel == c or rel.startswith(c + "/"):
                if best is None or len(c) > len(best):
                    best = c
        if best is None:
            # cmm/fms, cmm/use: directories under a component root that is itself a component
            parts = rel.split("/")
            for i in range(len(parts), 0, -1):
                cand = "/".join(parts[:i])
                if cand in components:
                    best = cand
                    break
        if best is not None:
            owned[best].add(rel)
    return {c: sorted(v) for c, v in owned.items()}


def camel(component):
    return "".join(p.capitalize() for p in re.split(r"[/.\-_]", component))


def property_key(component):
    return "egovframe.components." + component.replace("/", ".")


JAVA_HEADER = """/*
 * Generated by tools/generate_components.py from the upstream source at
 * upstream/egovframe-common-components. Do not edit; regenerate.
 */
"""


def gen_component_class(pkg, comp, info):
    cls = f"{camel(comp)}Configuration"
    key = property_key(comp)
    imports = sorted(info["classes"])
    idgn = sorted(info["idgn"])
    mappers = sorted(info["mappers"])
    external = sorted(info["external"])
    lines = [JAVA_HEADER, f"package {pkg};", ""]
    lines += [
        "import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;",
        "import org.springframework.context.annotation.Bean;",
        "import org.springframework.context.annotation.Configuration;",
        "import org.springframework.context.annotation.Import;",
        "",
        "import com.github.jinahya.egovframe.boot.autoconfigure.EgovIdGeneration;",
        "import com.github.jinahya.egovframe.boot.autoconfigure.EgovMappers;",
        "",
    ]
    doc = [
        "/**",
        f" * Upstream component package {{@code egovframework.com.{comp.replace('/', '.')}}}.",
        f" * Enabled by {{@code {key}.enabled=true}}; off unless set.",
        " * <p>",
        f" * Beans imported: {len(imports)} (own and by-name dependencies, transitively).",
        f" * Mapper directories: {', '.join(mappers) if mappers else 'none'}.",
        f" * ID generation contexts: {', '.join(idgn) if idgn else 'none'}.",
    ]
    if external:
        doc += [
            " * <p>",
            " * <b>What this component needs that neither upstream's Java nor the core auto-configuration",
            " * provides; the application must (bean names, or a library):</b>",
            f" * {', '.join('{@code ' + e + '}' for e in external)}.",
        ]
    doc += [" */"]
    lines += doc
    lines += [
        f'@ConditionalOnProperty(prefix = "{key}", name = "enabled", havingValue = "true")',
        "@Configuration(proxyBeanMethods = false)",
    ]
    if imports:
        lines.append("@Import({")
        lines += [f"        {c}.class," for c in imports]
        lines.append("})")
    if idgn:
        lines.append("@EgovIdGeneration({")
        lines += [f'        "{x}",' for x in idgn]
        lines.append("})")
    lines.append(f"public class {cls} {{")
    lines.append("")
    if mappers:
        bean = camel(comp)[0].lower() + camel(comp)[1:] + "Mappers"
        lines.append("    @Bean")
        lines.append(f"    EgovMappers {bean}() {{")
        lines.append("        return EgovMappers.of(")
        lines += [f'                "{m}"' + ("," if i < len(mappers) - 1 else "") for i, m in enumerate(mappers)]
        lines.append("        );")
        lines.append("    }")
        lines.append("")
    lines.append("}")
    return cls, "\n".join(lines) + "\n"


def gen_aggregator(pkg, names):
    lines = [JAVA_HEADER, f"package {pkg};", "",
             "import org.springframework.context.annotation.Configuration;",
             "import org.springframework.context.annotation.Import;", "",
             "/**", " * Imports every generated component configuration; each one is conditional on its own property.",
             " */", "@Configuration(proxyBeanMethods = false)", "@Import({"]
    lines += [f"        {n}.class," for n in names]
    lines += ["})", "public class EgovComponentConfigurations {", "",
              "    /** Property keys of every generated component, in catalog order. */",
              "    public static final java.util.List<String> PROPERTY_KEYS = java.util.List.of("]
    return lines


def main():
    ap = argparse.ArgumentParser()
    here = os.path.dirname(os.path.abspath(__file__))
    root = os.path.dirname(here)
    ap.add_argument("--upstream", default=os.path.join(root, "upstream", "egovframe-common-components"))
    ap.add_argument("--out", default=os.path.join(root, "egovframe-common-components-spring-boot-autoconfigure",
                                                    "src", "main", "java", "com", "github", "jinahya", "egovframe",
                                                    "boot", "autoconfigure", "components"))
    ap.add_argument("--doc", default=os.path.join(root, "docs", "COMPONENTS.md"))
    ap.add_argument("--package", default="com.github.jinahya.egovframe.boot.autoconfigure.components")
    ap.add_argument("--metadata", default=os.path.join(root, "egovframe-common-components-spring-boot-autoconfigure",
                                                         "src", "main", "resources", "META-INF",
                                                         "additional-spring-configuration-metadata.json"))
    args = ap.parse_args()

    java_root = os.path.join(args.upstream, "src", "main", "java", "egovframework", "com")
    res_root = os.path.join(args.upstream, "src", "main", "resources", "egovframework")
    spring_root = os.path.join(res_root, "spring", "com")
    mapper_root = os.path.join(res_root, "mapper", "com")

    classes = scan_java(java_root)
    components = find_components(java_root)
    idgn = scan_idgn(spring_root)
    mappers = mapper_dirs(mapper_root, components)

    by_name = {}
    for c in classes:
        if c.stereotype and c.bean_name:
            by_name.setdefault(c.bean_name, c)
    for c in classes:
        for m in c.bean_methods:
            by_name.setdefault(m, c)
    by_fqcn = {c.fqcn: c for c in classes}
    by_type = defaultdict(list)          # simple class or interface name -> stereotype classes
    for c in classes:
        if c.stereotype:
            by_type[c.simple].append(c)
            for i in c.implements:
                by_type[i].append(c)
    owned = defaultdict(list)
    for c in classes:
        if c.importable:
            o = owner_of(c.rel, components)
            if o:
                owned[o].append(c)

    results = {}
    for comp in components:
        if comp in EXCLUDED_COMPONENTS:
            continue
        seen = set()
        queue = deque(owned.get(comp, []))
        idgn_files, external, touched = set(), set(), set()
        while queue:
            c = queue.popleft()
            if c.fqcn in seen:
                continue
            seen.add(c.fqcn)
            touched.add(owner_of(c.rel, components))
            for t in c.types:
                for target in by_type.get(t.split(".")[-1], []):
                    if target.importable:
                        queue.append(target)
            for name in c.resources:
                if name in CORE_BEANS:
                    continue
                if name in by_name:
                    t = by_name[name]
                    if t.importable:
                        queue.append(t)
                    else:
                        external.add(name)
                elif name in idgn:
                    idgn_files.add(idgn[name])
                else:
                    external.add(name)
        if comp in EXTERNAL_LIBRARIES:
            external.add(EXTERNAL_LIBRARIES[comp])
        mdirs = set()
        for t in touched:
            mdirs.update(mappers.get(t, []))
        results[comp] = {"classes": seen, "idgn": idgn_files, "mappers": mdirs, "external": external,
                         "own": sorted(c.fqcn for c in owned.get(comp, []))}

    os.makedirs(args.out, exist_ok=True)
    for f in os.listdir(args.out):
        if f.endswith(".java"):
            os.remove(os.path.join(args.out, f))
    names = []
    for comp in sorted(results):
        cls, src = gen_component_class(args.package, comp, results[comp])
        open(os.path.join(args.out, cls + ".java"), "w", encoding="utf-8").write(src)
        names.append(cls)
    agg = gen_aggregator(args.package, names)
    keys = [property_key(c) for c in sorted(results)]
    agg += [f'            "{k}"' + ("," if i < len(keys) - 1 else "") for i, k in enumerate(keys)]
    agg += ["    );", "",
            "    /** Components needing what neither upstream's Java nor the core defines: bean names, or a 'library ...' entry. */",
            "    public static final java.util.Map<String, java.util.List<String>> EXTERNAL_BEANS = java.util.Map.ofEntries("]
    ext = [(property_key(c), sorted(results[c]["external"])) for c in sorted(results) if results[c]["external"]]
    agg += [f'            java.util.Map.entry("{k}", java.util.List.of({", ".join(chr(34) + e + chr(34) for e in v)}))'
            + ("," if i < len(ext) - 1 else "") for i, (k, v) in enumerate(ext)]
    agg += ["    );", "", "}", ""]
    open(os.path.join(args.out, "EgovComponentConfigurations.java"), "w", encoding="utf-8").write("\n".join(agg))

    os.makedirs(os.path.dirname(args.doc), exist_ok=True)
    with open(args.doc, "w", encoding="utf-8") as d:
        d.write("# Component catalog\n\n")
        d.write("Generated by `tools/generate_components.py` from the upstream source; do not edit.\n\n")
        d.write(f"Components: {len(results)} generated, {len(EXCLUDED_COMPONENTS)} excluded. ")
        d.write(f"Stereotype classes importable: {sum(1 for c in classes if c.importable)}. ")
        d.write(f"ID-generation contexts: {len(set(idgn.values()))}.\n\n")
        d.write("| Property (`.enabled`) | Package | Own beans | Imported | Mapper dirs | ID gen | External beans required |\n")
        d.write("|---|---|---|---|---|---|---|\n")
        for comp in sorted(results):
            r = results[comp]
            d.write(f"| `{property_key(comp)}` | `{comp.replace('/', '.')}` | {len(r['own'])} | {len(r['classes'])} | "
                    f"{', '.join(r['mappers']) or '-'} | {', '.join(sorted(x.replace('context-idgn-', '').replace('.xml', '') for x in r['idgn'])) or '-'} | "
                    f"{', '.join('`' + e + '`' for e in sorted(r['external'])) or '-'} |\n".replace('`library ', 'library `').replace(' (proprietary', '` (proprietary').replace('components)`', 'components)'))
        d.write("\n## Excluded components\n\n")
        for comp, why in sorted(EXCLUDED_COMPONENTS.items()):
            d.write(f"- `{comp.replace('/', '.')}`: {why}\n")
        d.write("\n## Classes never imported\n\n")
        d.write("Stereotype classes matching an exclusion pattern (web packages, schedulers, Twitter):\n\n")
        for c in sorted(classes, key=lambda c: c.fqcn):
            if c.stereotype and not c.importable:
                d.write(f"- `{c.fqcn}`\n")
        d.write("\n## Beans the core provides\n\n")
        d.write(", ".join(f"`{b}`" for b in sorted(CORE_BEANS)) + "\n")

    import json
    os.makedirs(os.path.dirname(args.metadata), exist_ok=True)
    props = []
    for comp in sorted(results):
        props.append({
            "name": property_key(comp) + ".enabled",
            "type": "java.lang.Boolean",
            "defaultValue": False,
            "description": f"Enable upstream component egovframework.com.{comp.replace('/', '.')} "
                           f"({len(results[comp]['classes'])} beans).",
        })
    json.dump({"properties": props}, open(args.metadata, "w", encoding="utf-8"), indent=2, ensure_ascii=False)

    ext_all = sorted({e for r in results.values() for e in r["external"]})
    print(f"components: {len(results)} generated, classes importable: {sum(1 for c in classes if c.importable)}, "
          f"external bean names: {len(ext_all)} -> {' '.join(ext_all)}")


if __name__ == "__main__":
    main()
