# Research hand-off (2026-10-16)

The research session's notes, kept verbatim as the record of what was verified and why; the
project as built is described in the [README](../README.md). Some statements here are superseded
(the module list, the PostgreSQL-first idea, the Boot line), and the "Status: planning" note is history.


eGovFrame (전자정부 표준프레임워크) **common components, headless, for Spring Boot.**

Upstream ships the common components only as WAR-packaged source that you copy into your project
with the eGovFrame IDE. This repository compiles that source, unmodified, into a jar, leaves the
JSP/controller half behind, and adds the Spring Boot wiring that the copy-the-source model never
needed: a one-time bridge to the eGovFrame runtime, and one property toggle per component.

This is an independent repackaging. It is not published or endorsed by the Ministry of the Interior
and Safety (행정안전부), the National Information Society Agency (한국지능정보사회진흥원, NIA), or the
eGovFrame Open Community. See [License](#license).

> **Status: planning.** Nothing here is built yet. This README is the hand-off from the research
> session of 2026-10-16 so a fresh session can start without re-deriving anything. The `pom.xml`
> currently in the repository is a template copied from another project (Oracle sample schemas,
> Boot 4.1.1); it has not been adapted and must not be taken as a decision. See
> [Open decisions](#open-decisions).

---

## 1. Why this exists

| | Runtime (RTE) | Common components |
|---|---|---|
| What it is | 24 thin library modules over Spring: service/DAO base classes, ID generation, property service, security adapter, paging taglib | 229 numbered business functions: login, authority, menu, code, board, file, audit logs, statistics, ... |
| How upstream delivers it | **Maven jars** on `https://maven.egovframe.go.kr/maven/`, groupId `org.egovframe.rte` | **WAR-packaged source** on GitHub, `<packaging>war</packaging>`; no jar anywhere |
| How you are meant to use it | declare a coordinate | tick components in the IDE wizard, which copies Java + JSP + mapper XML + Spring XML + DDL into your project |
| Problem for Spring Boot | none | no artifact to depend on; nothing activates even if you build one, because there is no auto-configuration and the package `egovframework.com.*` is outside your component scan |

The components cannot be used without the runtime (every service extends `EgovAbstractServiceImpl`
from `fdl-cmmn`, every DAO extends `EgovAbstractMapper` from `psl-dataaccess`). The runtime stands
alone. So: **the runtime is a dependency to resolve; the components are the work.**

Why upstream ships a WAR (inference from the artifacts, not a stated rationale): the repo is a
runnable demo portal (749 JSPs, 166 controllers, Tiles); components are UI-inclusive; the intended
model is copy-and-customize; public-sector contracts hand over source; component boundaries are
soft (shared tables, shared `COMTECOPSEQ` sequence table, by-name injection); it bundles proprietary
jars that no repository can host; and it predates Spring Boot (2009, Spring 2.5, iBATIS).

Who this is for: teams building eGovFrame-compliant systems on Spring Boot with a REST backend and a
separate frontend, who need the service and data half of the components (audit logs, authority,
code, user tables) and not the screens. Not for the classic JSP-template path.

---

## 2. Upstream facts (verified 2026-10-16)

### Organisation and channels

- Funded/owned by MOIS; operated by the eGovFrame Center at NIA; developed in the open as
  "The eGovFrame Open Community". Source headers say `MOPAS` (the ministry's earlier name) and
  `The eGovFrame Open Community`.
- Portal and wiki: `https://www.egovframe.go.kr`
- GitHub: `https://github.com/eGovFramework` — 24 repositories, all Apache-2.0
- Maven host: `https://maven.egovframe.go.kr/maven/` — **the only binary source**. Not on Maven
  Central (`repo1.maven.org/maven2/org/egovframe/` is 404; Central search for `egovframe*` returns 0).

### Maven host behaviour (matters for mirroring)

- No directory listing (403), no `maven-metadata.xml` (404).
- Returns 400 to curl without a browser-like `User-Agent`; 200 with `-A 'Mozilla/5.0'`.
- Intermittent timeouts observed. Probe one artifact at a time; mirror once, then work offline.

### Versions on the host (jar probed for all 24 RTE modules)

| Version | RTE (24 modules) | parent POM `org.egovframe.web:egovframe-web-config-parent` | Upload date | GitHub tag `egovframe-runtime` |
|---|---|---|---|---|
| 5.0.0 | all | yes | 2026-03-11 | `v5.0.0-Final` 2026-05-18 — Spring 6.2.11, Spring Security 6.5.5, Boot 3.5.6 target |
| 5.0.1 | all | yes | 2026-09-01 | `v5.0.1-Final` 2026-09-03 — "NCSC & NIA security review applied" |
| 5.0.2 | all | yes | 2026-09-21 | `v5.0.2-Final` 2026-09-22 — "security review and improvements" |
| 5.0.3 | none | no | | |
| **5.1.0** | **all** | yes | 2026-09-28 … 2026-10-11 | **none yet**; `main` POM still says 5.0.0. Launch event announced for **2026-11-30** (portal notice nttId=1966, posted 2026-10-16) |
| 5.1.1, 5.2.0 | none | no | | |

5.1.0 is a bigger change than a patch; see [§9 v5.1 findings](#9-v51-findings-2026-10-16) for the
full picture (3 new AI modules, eGov-forked Spring Security / Integration / Spring AI, rte-root as a
BOM). Treat 5.1 as a follow-up after the November launch; the common components have no 5.1 release.

The 24 RTE modules:

```
fdl: security access crypto cmmn xml idgnr excel property string logging reactive filehandling
psl: dataaccess data-jpa data-mongodb reactive-cassandra reactive-mongodb reactive-r2dbc reactive-redis
ptl: reactive mvc
itl: integration webservice
bat: core
```

Coordinates: `org.egovframe.rte:egovframe-rte-<module>:<version>`. **No BOM**; the parent POM
`egovframe-web-config-parent` manages all 24 via `${egovframe.rte.version}` and is the thing to
import as a platform.

### Common components (`eGovFramework/egovframe-common-components`)

| Tag | Date | Builds against | Notes |
|---|---|---|---|
| v5.0.6 | 2026-07-21 | parent 5.0.0, RTE 5.0.0 (fdl-security pinned 5.0.1) | own POM still says `<version>5.0.0</version>` — version string lags the tag |
| **v5.0.7** | 2026-10-07 | **parent 5.0.2, RTE 5.0.2** | POM says 5.0.7. "2026 contributions; function-based access rights; ownership verification for key functions" |

v5.0.7 contents: 1,097 Java files under `src/main/java`, 1,224 mapper XMLs (153 per DBMS × 8 DBMS:
postgres, mysql, maria, oracle, tibero, altibase, cubrid, goldilocks), 749 JSPs, 51 files under
`script/` (DDL/DML/comments per DBMS), 83 POM dependencies, `<packaging>war</packaging>`.

Bundled jars in `src/main/webapp/WEB-INF/lib/project/`, **not in any repository, not Apache**:

```
smeapi-2.7.0        SMS gateway client (x3.client.smeapi.*), used by cop.sms      — proprietary
onepass-2.0.0       login                                                       — proprietary
gpkisecureweb-1.0.4.9, libgpkiapi-1.4.0   Korean government PKI login            — proprietary
ojdbc6-11.2.0.3, tibero5-5.0.0, altibase-7.1.0, cubrid-10.2.0, goldilocks8-8.0.0   JDBC drivers — vendor
```

Also required from the eGovFrame host: `egovframework.com.ems:sndng-mail:1.0` (XMLBeans-generated
mail schema classes; stub POM "created by Sonatype Nexus", **no license declared**).

Counts: wiki lists **123 deploy zips / 229 numbered components** (1–229, no gaps). Upstream README
says "253 common functions"; that number appears nowhere in the checked-out source. Use 229.

Wiki source for the component catalog (DokuWiki raw export, 57,213 bytes, reachable 2026-10-16):

```
https://www.egovframe.go.kr/wiki/doku.php?id=egovframework:com:v5.0:init_deploy_file&do=export_raw
```

### License

- Runtime, common components, and every RTE POM: **Apache License 2.0**. Wiki:
  "전자정부 표준프레임워크는 Apache 2.0 라이선스를 따르고 있는 오픈소스 프로그램입니다."
- Only 41 of 1,089 Java files carry the Apache header text; the LICENSE file and POM `<licenses>`
  are authoritative. Copyright holders: MOPAS/MOIS, The eGovFrame Open Community.
- Redistribution, modified or not, is permitted. Obligations: keep notices, ship a NOTICE naming
  MOIS and the eGovFrame Open Community, state this is an independent repackaging.
- The nine bundled jars and `sndng-mail` are **not** covered. They cannot be published. Components
  that need them (`cop.sms`, GPKI login) must be excluded or have those dependencies `optional`.
- Maven Central namespace rule: you must control the groupId's domain. `org.egovframe` /
  `kr.go.egovframe` are government-owned. Publish under your own (`com.github.jinahya`,
  `io.github.jinahya`). Central also refuses artifacts whose dependencies are not on Central, so
  **publishing a starter to Central forces relocating the RTE jars too** (same bytes, your groupId,
  POM pointing at upstream SCM).

---

## 3. What the predecessor project did (the recipe)

The research and a working first implementation live in a private monorepo
(`viralpick/AgentOS-FDE-ksqa-platform`, module `backend`, package `kr.or.ksqa`). Paths below are
relative to `backend/`. That code is the project's own and can be carried over; upstream code was
never modified there.

Six moves, each solving one problem the WAR model creates:

### Move 1 — Turn WAR source into a library jar

`ext/egovframe-common-components-build/build.gradle` (178 lines) + `buildSrc/src/main/groovy/egovframe-common-components.gradle` (57 lines)

- Standalone Gradle build, **not** a subproject of the app (`settings.gradle` has one project).
- `sourceSets.main.java.srcDirs = ["$upstream/src/main/java"]`, resources likewise; `webapp` excluded
  → no JSPs, and the 166 `@Controller` classes are left out of wiring.
- `api platform('org.egovframe.web:egovframe-web-config-parent:5.0.0')` — no version chosen locally.
- File repository for the bundled jars: `maven { url "file://$upstream/src/main/webapp/WEB-INF/lib"; metadataSources { artifact() } }` (without `metadataSources` → `Could not find project:smeapi:2.7.0`).
- Dependencies = upstream POM's `<dependencies>` transcribed (ten RTE modules, log4j2, slf4j bridges,
  dbcp2, jstl, jasypt, cglib, quartz, antlr4, oro, icu4j, commons-net, commons-email2, sndng-mail,
  jakarta.mail, ldapsdk, jodconverter, fileupload2, twitter4j, ajaxtags, lombok compileOnly, ...).
- Publishes with upstream coordinates `egovframework:egovframe-template-common-components:<ver>` to a
  committed Maven-layout dir `buildRepository/`; task `publishEgovFrameCommonComponents`.
- Output for v5.0.6: jar 4.4 MB, 1,104 classes, 1,224 mapper XMLs, 301 properties. **No LICENSE or
  NOTICE inside the jar** — a defect to fix here.
- Gotcha: the standalone build needs Java 21 (`JAVA_HOME`); Gradle 8.14.5 fails under JDK 27
  ("Unsupported class file major version 71") and the root `gradle.properties` does not pin a Java home.

### Move 2 — Take the jar whole, cut its dependency tail

`build.gradle` of the app:

```groovy
implementation('egovframework:egovframe-template-common-components:5.0.0') {
    exclude group: 'org.egovframe.rte'          // drops its 10 RTE modules; re-add only what is needed
}
implementation "org.egovframe.rte:egovframe-rte-fdl-cmmn:${v}"
implementation "org.egovframe.rte:egovframe-rte-fdl-crypto:${v}"     // ARIA; no JCA equivalent
implementation "org.egovframe.rte:egovframe-rte-fdl-idgnr:${v}"
implementation "org.egovframe.rte:egovframe-rte-fdl-logging:${v}"
implementation "org.egovframe.rte:egovframe-rte-fdl-property:${v}"
implementation("org.egovframe.rte:egovframe-rte-fdl-security:${v}") { /* excludes */ }
implementation("org.egovframe.rte:egovframe-rte-psl-dataaccess:${v}") { exclude module: 'ibatis-sqlmap' }
implementation("org.egovframe.rte:egovframe-rte-ptl-mvc:${v}") { /* excludes */ }
```

Why each exclusion: `fdl-security` auto-configures a filter chain → every request 401;
`ptl-reactive` → WebFlux; `bat-core` → Spring Batch; `fdl-excel` → POI (re-declare POI directly if
needed); **ten RTE modules carry log4j2 at compile scope** (`fdl-logging fdl-reactive psl-data-mongodb
psl-reactive-* ptl-mvc ptl-reactive bat-core`) → two SLF4J bindings → logger init fails, so a global
log4j exclusion block exists; `psl-data-jpa` is one class (`EgovJpaRepository`, re-declares
`JpaRepository`) that drags `mysql-connector-j` and an older Hibernate → never include;
`psl-dataaccess` is required (jar's `mapper-config.xml` registers typeAlias `egovMap` → `EgovMap`,
and 149 DAOs extend `EgovComAbstractDAO → EgovAbstractMapper`) but exclude `ibatis-sqlmap`.
Transitively also arrives: `fdl-string`. RTE `fdl-security` and the 5.0.1 pin in the CC POM: decide
at wiring time whether to follow 5.0.0 or 5.0.1 (for v5.0.7 this is moot: everything is 5.0.2).

### Move 3 — Make the classpath inert, then enable by name

`src/main/java/kr/or/ksqa/config/egovframe/` — 63 `Com*Config` classes, `_CoreConfiguration`,
`_MapperLocations`, `package-info.java` (the rules).

One `@Configuration` per upstream component. Pattern (actual class for component 111 접속로그관리):

```java
@Import({EgovLoginLogServiceImpl.class, LoginLogDAO.class})
@ImportResource("classpath:egovframework/spring/com/idgn/context-idgn-LoginLog.xml")  // when the component uses ID generation
@ConditionalOnProperty(prefix = "egovframe.com-sym-log-clg", name = "enabled", havingValue = "true")
@Configuration
class ComSymLogClgConfig {
    @Bean _MapperLocations comSymLogClgMapperLocations() {
        return _MapperLocations.of("sym/log/clg/EgovLoginLog_SQL_postgres.xml");
    }
}
```

Rules: key = class name in kebab-case; **default off** (no `matchIfMissing`); a failed condition
means the class is not even parsed, so its `@Import`/`@ImportResource`/`@Bean` all stay out and its
statements are 0; `@ComponentScan`, if ever used, stops at `.service` or JSP controllers come along.
Naming: `Com<Package>Config`, e.g. `ComUatUapConfig`, `ComSecRamConfig`, `ComUssUmtConfig`.

Components turned on in that app (what a minimal REST+JWT system actually needs):

| Toggle | Component | Used for |
|---|---|---|
| `com-sym-log-clg` | 111 접속로그관리 | login audit log (`COMTNLOGINLOG`), written/read by upstream `LoginLogDAO` |
| `com-sym-log-plg` | 112 개인정보조회로그 | privacy-access audit log (`COMTNPRIVACYLOG`), upstream `PrivacyLogDAO` |
| `com-sec-ram` | 권한관리 | `AuthorManageDAO.selectAuthor` existence check |
| `com-sec-rmt` | 롤관리 | `RoleManageDAO` CRUD |
| `com-uss-umt` | 사용자관리(부서) | `DeptManageDAO.selectDeptManage` existence check |

### Move 4 — Satisfy upstream's by-name contracts once

`_CoreConfiguration.java` (187 lines). Upstream injects by **bean name**, not type.

- `egov.dataSource`: a `static BeanFactoryPostProcessor` registering an **alias** of Boot's
  `dataSource` (a second DataSource bean would break Boot's `@ConditionalOnSingleCandidate`).
- `egov.sqlSession`: one `SqlSessionFactoryBean` built from the union of every enabled
  `_MapperLocations` contribution. Consequence: Boot's MyBatis auto-config backs off and
  `mybatis.mapper-locations` is silently ignored.
- `MessageSource` (`@Primary`): RTE message bundles (`fdl-idgnr` `idgnr.properties`, `fdl-cmmn`
  `message-common.properties`) — without them ID generation dies on its first debug log with
  `NoSuchMessageException`. Must be `@Primary` because `EgovMessageSource extends
  ReloadableResourceBundleMessageSource` is also a `MessageSource`.
- `egovMessageSource` with `useCodeAsDefaultMessage(true)` **overwrites Boot ProblemDetail
  `title`/`detail` with message keys** — wrap it (`ProblemDetailSafeMessageSource`). Slice tests do
  not show this.
- `leaveaTrace` / `TraceHandlerService`: empty, required by `EgovAbstractServiceImpl` (all 153
  ServiceImpls).
- Do **not** `@ImportResource` upstream `context-datasource.xml` / `context-mapper.xml` — each makes
  its own DataSource/SqlSessionFactory beside Boot's.

### Move 5 — Adopt the schema as a foreign artifact

`db/docker-entrypoint-initdb.d/0200-egovframe.sql` = upstream `script/ddl/postgres/com_DDL_postgres.sql`
verbatim; ordered before project tables; **not re-runnable** (no `IF NOT EXISTS`). Tables of
switched-off components commented out. Project changes to upstream tables go into
`0201-egovframe-alter.sql` as `ALTER` (e.g. user id 50 chars; drop/recreate the `COMVNUSERMASTER`
view around it). Audit tables have no JPA entity so `ddl-auto: validate` ignores them; a startup
`AuditSchemaGuard` checks they exist. `0600-ksqa-common.sql` adds the project's sequence rows /
indexes for the audit tables right after login numbering.

### Move 6 — Lock the wiring with tests

`src/test/java/kr/or/ksqa/config/egovframe/` — 63 files: one `Com*ConfigIT` per config (enables only
its own component, sweeps every `<select>` in its mapper against the schema: `MapperSelectSweep`),
`_AllConfigsContextIT` (all 60 in one context, 2.4 s startup, 58 classes / 122 tests green at
2026-08-26), `_CoreConfigurationTest`, three `*ConfigTest` for stateless utilities. App-level:
`EgovLogFeatureToggleTest` (required toggles are on), `BeanNameCollisionTest` (duplicate simple class
names kill startup; slice tests cannot see it). Note: 58 ITs are `@Disabled` since 2026-09-22 because
their tables were commented out of the schema; they are intact and reusable.

### The scan-all counter-experiment (issue #3, do not repeat)

`doc/egovframe/_EGOVFRAME_SCAN_ALL.md`. A single `@ComponentScan(basePackages = "egovframework.com",
useDefaultFilters = false)` including `@Service/@Repository/@Component`, excluding `@Controller`
(166), `@Configuration` (3, incl. `EgovCsrfSecurityConfig` which competes with your filter chain),
`uss.ion.tir` (Twitter: `@Autowired EgovXClient` has no implementation in the distribution),
`.*Scheduling` (13) + `sym.bat` + `sym.sym.bak` + `cop.sms` (need `mntrngMessage`,
`mntrngMailSender`, `*Trigger` beans defined only in `context-scheduling-*.xml`), `ext.ldapumt`.

Result: boots; 299 `egovframework.com` beans, 959 bean definitions, 149 mapper namespaces, 2,432
statements, 2.8 s startup, 182 schema objects needed, 9 of 722 `<select>` fail (0 missing relations).
**Isolation cannot be proven**, and **toggles and scan cannot coexist**: `@Import` registers the FQCN
as bean name, scan registers `@Service("name")` → same type twice → `NoUniqueBeanDefinitionException`.
Decision: toggles. Also, `uat.uia` (login) pulls `cop.ems` (mail) transitively via
`sndngMailRegistService → egovSndngMailService → egovMultiPartEmail`.

---

## 4. The taxonomy (the spec for the autoconfigure layer)

`doc/egovframe/_EGOVFRAME_TAXONOMY.md`, 7,386 lines, built 2026-08-26 from the wiki export and
source commit `23d0188` (= v5.0.6). Sections: 출처와 재현 / "필요한 테이블"이 두 가지다 / 도출 방법 /
전체 규모 / 결과 타입 / 발견한 결함 / 교차검증 / 공유 요소 색인 / 기능 간 데이터 의존 / 색인(123) /
배포파일별 상세 / 아직 비어 있는 것.

### Edges (how the closure was computed)

| # | Edge | Found by | Size at v5.0.6 |
|---|---|---|---|
| 1 | service → service / DAO | `@Resource(name=…)` + the field's **type** | 875 (1 unresolved: `SimpleSSLMail`) |
| 2 | DAO → mapper namespace | string literals in `selectList("<Ns>.<id>")` etc. | 148 |
| 3 | mapper → table | `FROM` / `JOIN` / `INSERT INTO` / `UPDATE` in XML | 172 distinct tables |
| 4 | service → ID-generation table | `<property name="table">` in 95 `context-idgn-*.xml` | 115 beans, all `COMTECOPSEQ` |
| 5 | table → table | `CREATE VIEW … FROM`, `REFERENCES` in DDL | 1 view (`COMVNUSERMASTER`), 54 tables with FKs |
| 6 | AOP → service | pointcuts in `context-syslogaop.xml`, `context-dtauseaop.xml` | 1 global (154 targets), 1 specific |
| 7 | service → by-type injection | `@Autowired` fields (no name) | 1 (`EgovXServiceImpl → EgovXClient`) |

Traps learned the hard way: edge 4 lives only in Spring XML (miss it → `COMTECOPSEQ` absent); edge 7
is invisible to name-following (surfaces as "required a bean of type … could not be found"); finding
DAOs by naming convention misses 32 (`*DAO` 122 vs `*Dao` 32).

### Two kinds of "needed table"

Wiki `포함 테이블` = what the installer **creates** (e.g. `COMTCCMMNCODE` is in 111 of 123 zips).
Derived tables = what the SQL **touches** at runtime (`COMTCCMMNCODE` is read by exactly one
namespace, `CmmnCodeManage`). Install by the wiki, wire by the derivation. Do not mix.

### Per-component entry yields

deploy zip, package, services, mapper namespaces, tables with share counts `(n)`, VOs, and a collapsed
wiring block: extra RTE coordinates if any, a ready `@Configuration` (`@Import` list + mapper path),
and a table of beans defined only in upstream XML that must be hand-made (e.g. `EgovLoginLogAspect`,
`LoginLog` for component 111). 111 of 123 zips need no RTE beyond the five baseline modules; 12 need
`fdl-crypto` (6), `fdl-excel` (5) or `fdl-security` (1). 81 of 229 components touch no DB (stateless
utilities).

### Cross-cutting findings

- Shared vs. exclusive: services 17 shared / 126 exclusive (88 %), mappers 17 / 124 (87 %), tables
  54 / 116 (68 %). The shared ones (`EgovCmmUseServiceImpl`+`CmmUseDAO`+`EgovCmmUse_SQL_postgres.xml`
  in 25 configs; `EgovFileMngServiceImpl`+`FileManageDAO` in 2) decide what goes in the core module.
- Duplication across configs is harmless in `@Import` (Spring dedups by class) and mapper paths
  (MyBatis skips a read resource), **fatal** in `@ImportResource` (re-read per config) and `@Bean`
  method names (= bean name). All 6 duplicates observed were in the harmless layers.
- Data dependencies invisible in mappers: login-log queries join `COMVNUSERMASTER` — without user
  rows the log reads empty even with a perfect schema.
- Upstream defects found (would hit any source-copy user at runtime): a namespace typo making one
  statement unreachable; duplicate namespaces; `Adress`/`Address` misspelling fossilised in bean
  names/namespaces/statements; a mapper referencing another table's column (SQL cannot run);
  `GROUP BY` outside its `<if>` (looks like a schema error when the parameter is empty); `82
  Twitter연동` unwireable (no `EgovXClient` implementation).

### What is NOT preserved

**The derivation scripts were never committed.** Only results and the method survive. For v5.0.7 the
closure must be recomputed, which means writing the scripts again from the table above. Once written,
keep them in this repo so regenerating the taxonomy per upstream tag is a build step.

---

## 5. Target shape of this repository

Product: upstream's common components as versioned Maven artifacts a Spring Boot project can depend
on, instead of source it has to copy. Scope: service/DAO/SQL/VO/DDL half only. No JSPs, no
controllers, no bundled proprietary jars; `cop.sms` and GPKI login excluded or `optional`.

Proposed modules:

```
egovframe-common-components                              rebuilt upstream jar; version = upstream tag (5.0.7); + LICENSE/NOTICE
egovframe-runtime-spring-boot-autoconfigure              core bridge: egov.dataSource alias, egov.sqlSession, message bundles, LeaveaTrace,
                                                         ProblemDetail-safe MessageSource, log4j policy — always on; usable without any component
egovframe-common-components-spring-boot-autoconfigure    one @Configuration per component, egovframe.<component>.enabled, default off;
                                                         spring-configuration-metadata for the toggles
egovframe-common-components-spring-boot-starter          aggregates the above + the required RTE modules with exclusions applied
egovframe-common-components-schema-postgresql            upstream DDL as classpath resources + alterations file (+ other DBMS later)
egovframe-common-components-bom                          pins a coherent RTE + components pair (today: RTE 5.0.2 + CC v5.0.7)
egovframe-runtime-relocation (only if targeting Central) the 24 RTE jars, same bytes, this groupId, POM → upstream SCM
```

Versioning rule: the rebuilt jar carries upstream's tag; autoconfigure/starter carry their own
version with the upstream version in the BOM, so a wiring fix is not disguised as an upstream release.

Target pair: **common components v5.0.7 on RTE 5.0.2** (what its POM says). Do not pair v5.0.7 with
RTE 5.1.0 until upstream does.

---

## 6. Inputs checklist for the redo

| Input | Status | Where |
|---|---|---|
| Common-components source v5.0.7 | fetch | `git clone --branch v5.0.7 https://github.com/eGovFramework/egovframe-common-components` (predecessor has v5.0.6 as a submodule) |
| RTE 5.0.2, all 24 modules (jar, pom, sources) | fetch, mirror once | `https://maven.egovframe.go.kr/maven/org/egovframe/rte/egovframe-rte-<m>/5.0.2/` with a browser UA |
| parent POM 5.0.2 | fetch | `…/org/egovframe/web/egovframe-web-config-parent/5.0.2/` |
| `egovframework.com.ems:sndng-mail:1.0` | fetch | same host, `egovframework/com/ems/sndng-mail/1.0/` |
| 9 bundled jars | in source tree | `src/main/webapp/WEB-INF/lib/project/**` |
| PostgreSQL DDL | in source tree | `script/ddl/postgres/com_DDL_postgres.sql` (+ DML, comments) |
| Wiki catalog export (57 KB) | fetch, snapshot into repo | URL in §2 |
| Build recipe | carry over | predecessor `ext/egovframe-common-components-build/build.gradle` (Gradle; this repo is Maven — transcribe) |
| Core bridge | carry over | `_CoreConfiguration.java`, `_MapperLocations.java`, `ProblemDetailSafeMessageSource` |
| 63 toggle configs + 63 tests | carry over, re-verify against v5.0.7 | `config/egovframe/`, `src/test/.../config/egovframe/` |
| Taxonomy docs | carry over as method | `doc/egovframe/_EGOVFRAME_TAXONOMY.md`, `_EGOVFRAME_INTEGRATION.md`, `_EGOVFRAME_SCAN_ALL.md`, `_EGOVFRAME.md` |
| Derivation scripts | **rewrite** | none exist |

Nothing in the predecessor's Gradle cache beyond nine RTE 5.0.0 jars; the eGovFrame
"gettingstarted-maven-repository" download is a Maven local-repo cache of a sample project (only
fdl-cmmn and fdl-logging) and adds nothing.

Probe used to verify host contents (zsh):

```sh
UA='Mozilla/5.0'
for m in fdl-cmmn fdl-security …; do for v in 5.0.2 5.1.0; do
  curl -s -A "$UA" -o /dev/null -w "%{http_code} %header{last-modified}\n" --max-time 25 \
    "https://maven.egovframe.go.kr/maven/org/egovframe/rte/egovframe-rte-$m/$v/egovframe-rte-$m-$v.jar"
done; done
```

---

## 7. Open decisions

1. **Spring Boot line.** The template `pom.xml` says Boot 4.1.1 and `mybatis-spring-boot 4.1.0`
   (Boot 4 / Spring 7 line). RTE 5.0.x is compiled against **Spring Framework 6.2.11 / Boot 3.5.6**
   and the predecessor pinned `mybatis-spring-boot-starter 3.0.5` precisely because 4.x targets
   Boot 4. Running RTE 5.0.2 on Spring 7 is untested. Decide: Boot 3.5.x (matches upstream, safe) vs
   Boot 4 (needs verification of `fdl-cmmn`, `psl-dataaccess`, `fdl-security` against Spring 7 and
   Spring Security 7). The `maven.compiler.release` 25 in the template is also above upstream's
   Java 17/21 floor; harmless for consumers on 25, exclusionary otherwise.
2. **Publication target.** Central (requires relocating the RTE, sources+javadoc jars, GPG, full POM
   metadata; biggest reach) vs. GitHub Packages / private repo (RTE stays upstream; one more host
   for consumers; competes with "just copy the source" on convenience).
3. **Build tool for the rebuilt jar.** Predecessor recipe is Gradle; this repo is Maven. Maven can do
   the same with `build-helper-maven-plugin` (`add-source`/`add-resource` pointing at a git submodule
   or a downloaded tag) and a `file://` repository with `<layout>default</layout>` for the bundled jars.
4. **How many components to wire in v1.** All 60-odd (predecessor did) vs. the handful with
   compulsory use (audit logs, authority, role, user, code, menu). The taxonomy makes "all" cheap
   once the scripts exist.
5. **Schema delivery.** Raw upstream DDL only, or also a Flyway/Liquibase-friendly split with
   `IF NOT EXISTS`. Upstream DDL is not re-runnable.
6. **Multi-DBMS.** Mapper XMLs exist for 8 DBMS; the single `SqlSessionFactory` picks one suffix
   (`_SQL_postgres.xml`). Property-select the suffix, or ship PostgreSQL only first.

---

## 8. Suggested first steps for the next session

1. Replace the template `pom.xml` with a real parent for the modules in §5 (decide Boot line first).
2. Script the mirror: RTE 5.0.2 ×24 (+5.0.0, 5.1.0 if wanted), parent POMs, `sndng-mail`, into a
   Maven-layout directory; snapshot the wiki export. Commit or `.gitignore` per decision 2.
3. Add the v5.0.7 source (submodule or fetched tag) and get `egovframe-common-components-5.0.7.jar`
   building with LICENSE + NOTICE inside and proprietary deps `optional`.
4. Port `_CoreConfiguration` into the runtime autoconfigure module; smoke-test on an empty Boot app
   with the audit-log component (111) and the upstream PostgreSQL DDL.
5. Write the taxonomy derivation scripts (edges 1–7, §4), run on v5.0.7, diff against the v5.0.6
   results in the predecessor doc.
6. Generate the per-component configs from the derivation output instead of hand-transcribing.
7. Port the per-component IT pattern (`MapperSelectSweep`) so every `<select>` runs against the DDL.

---

## License

Code in this repository: Apache License 2.0. Upstream eGovFrame runtime and common components:
Apache License 2.0, Copyright MOPAS/MOIS and The eGovFrame Open Community — redistributed here
unmodified, as an independent repackaging; see NOTICE (to be added). Bundled third-party jars found in
upstream's `WEB-INF/lib` are **not** redistributed.

---

## 9. v5.1 findings (2026-10-16)

Everything public about the upcoming version, gathered from the portal notice, the Maven host, and
the GitHub repositories. No release notes exist yet.

### The announcement

Portal notice nttId=1966, posted 2026-10-16: "2026 전자정부 표준프레임워크 신규 버전 발표회", Monday
2026-11-30 14:00–18:00, YouTube live, hosted by MOIS, run by NIA. Body says only that v5.1 will be
unveiled alongside technology-trend talks; "자세한 행사 내용은 추후 재공지 예정". Nothing else on the
portal mentions 5.1. Press coverage found so far is all about the 5.0 launch (Dec 2025).

### The artifacts are already on the Maven host

Uploaded between 2026-09-28 and 2026-10-13, i.e. six to nine weeks before the launch:

| What | Where | Uploaded |
|---|---|---|
| 24 existing RTE modules at 5.1.0 | `org/egovframe/rte/egovframe-rte-*/5.1.0/` | 2026-09-28 … 10-05 |
| **3 new RTE modules** at 5.1.0 | `egovframe-rte-ai-core`, `egovframe-rte-ai-langchain4j`, `egovframe-rte-ai-springai` | 2026-10-13 |
| `egovframe-rte-root:5.1.0` (BOM) | lists 27 modules | |
| `egovframe-web-config-parent:5.1.0` | now **imports rte-root** instead of listing 24 modules | 2026-10-11 |
| eGov-forked Spring artifacts | see below | 2026-09-11 (security) |

GitHub `egovframe-runtime` has **no 5.1 tag or branch**; `main` POM still says 5.0.0; the last merged
PRs (2026-10-01, #477–#483) are 5.1 feature work. So the binaries exist before the source is tagged.
`egovframe-common-components` has no 5.1 ref; v5.0.7 targets 5.0.2.

### Platform versions, rte-root 5.0.2 → 5.1.0

| | 5.0.2 | 5.1.0 |
|---|---|---|
| Java | 17 | 17 (unchanged floor) |
| Spring Framework | 6.2.11 | **6.2.19** (still 6.2 line, not Spring 7) |
| Spring Security | 6.5.x | **6.5.11-egov.1** (eGov fork) |
| Spring Integration | | **6.5.10-egov.1** (eGov fork) |
| Spring Batch | 5.2.3 | 5.2.6 |
| Spring Data | | 2025.0.13 |
| MyBatis / mybatis-spring | | 3.5.19 / 3.0.6 |
| Hibernate | | 6.6.58.Final |
| Jackson | 2.18.2 | 2.22.3 |
| JUnit | 5.12.1 | 6.1.3 |
| Servlet API | 5.0.0 | 6.0.0 |
| Spring AI | (5.0.2 parent had LangChain4j BOM only) | 1.1.8 + fork **1.1.8-egov.1** for 5 artifacts |
| LangChain4j | BOM | 1.18.1, managed individually (Boot starters excluded) |
| POI / PDFBox / hwplib / hwpxlib | | 5.5.1 / 3.0.7 / 1.1.9 / 1.0.8 (Korean HWP/HWPX extraction) |

Implication for this repo: **5.1 stays on Boot 3.5-compatible Spring 6.2.** The Boot 4 question in
§7 is not forced by upstream.

### eGov forks of Spring, on the eGovFrame host

The host serves patched builds under Spring's own groupIds with an `-egov.1` qualifier:

```
org.springframework.security:spring-security-{core,config,…}:6.5.11-egov.1   uploaded 2026-09-11
org.springframework.integration:spring-integration-core:6.5.10-egov.1
org.springframework.ai:{spring-ai-commons, spring-ai-template-st, spring-ai-model,
                        spring-ai-pdf-document-reader, spring-ai-transformers}:1.1.8-egov.1
```

All five Spring AI forks resolve (200). The POM comment ("`.m2` 에 egov 빌드가 있는 spring-ai-* 는 BOM
이 아닌 개별 관리") describes how the maintainers build, not an availability gap — the earlier worry in
this README that part of 5.1 would be unresolvable was wrong. Their POMs are Spring's originals with
only the version changed; no changelog explains the patches. Consequences: (a) `fdl-security:5.1.0`
depends on the forked Spring Security, so a 5.1 consumer pulls Spring Security from the eGovFrame
host, not Central; (b) these forks are **not relocatable to Central** under `org.springframework.*`,
so a Central-published starter on 5.1 would have to exclude them and accept Spring's own 6.5.11
instead, which is a behavioural risk until the diff is known.

### The three new AI modules (what 5.1 is actually about)

`egovframe-rte-ai-core` (117 KB, 41 classes): text extraction for PDF/DOCX/HWP/HWPX/Markdown with an
extraction budget, Korean sentence chunking, text normalisation, document hashing (in-memory and JDBC
store, `schema-ansi.sql`/`schema-hsqldb.sql`), PII masking (RRN rules, Luhn), a prompt-injection guard
with a bundled lexicon (`injection-lexicon.txt`), lexical search with PostgreSQL `pg_trgm`
(`schema-postgresql-pg_trgm.sql`) and RediSearch backends, RRF fusion for hybrid retrieval, AI audit
and error classification/fallback. Depends on poi-ooxml, pdfbox, hwplib, hwpxlib, jedis, spring-jdbc.

`egovframe-rte-ai-langchain4j` (60 KB, 21 classes): the same concerns bound to LangChain4j — document
loader/transformers (normalising, PII-masking), embedding-store writer, hybrid content retriever,
JDBC chat-memory store, JDBC segment lookup, injection guardrail, observation listener, query
compression prompt.

`egovframe-rte-ai-springai` (45 KB, 18 classes): the same bound to Spring AI — document reader,
vector-store writer, hybrid document retriever, injection-guard advisor, reasoning-stripping output
converter, query-compression template.

So 5.1 = "RAG building blocks for Korean public documents" on top of the 5.0 Spring AI/LangChain4j
introduction, plus dependency refresh and security-patched Spring forks.

### Non-AI changes merged on `main` after 5.0.2 (will land in 5.1)

- `EgovRestExceptionHandler` (ptl-mvc) and WebFlux `EgovExceptionHandler` (ptl-reactive) stop turning
  Spring standard exceptions into 500: status and headers preserved, method-parameter validation →
  400, auth exceptions delegated, new codes E012 (415) and E013 (413).
- `fdl-idgnr` ships reference DDL for the ID table (`schema-ansi.sql`, `schema-hsqldb.sql`).
- `psl-data-jpa`: `EgovMdcAuditorAware` fills created/modified-by from SLF4J MDC `userId`.
- `psl-reactive-r2dbc`: `EgovReactiveBaseEntity`, `EgovContextReactiveAuditorAware`,
  `EgovR2dbcRepository` constructor taking DatabaseClient/Dialect/Converter.
- `fdl-reactive`: `EgovReactiveUserIdWebFilter` putting the principal into Reactor Context.
- Open at the time of writing: `EgovMyBatisBatchItemWriter` Map-item fix (#490), Jackson 2.18.11 /
  2.21.7 bumps, jsoup 1.23.2.

### Common components side

No 5.1 yet. v5.0.7 (notice nttId=1965, 2026-10-06) is a security release: IDOR fixes, per-function
access rights, ownership verification (`@RequireAdmin` on controllers, `EgovAuthorizationHelper`, URL
roles from DB + `egov-security-config.properties` `permitAllList`). Open PRs on `main` (Oct 2026):
initial-account lockout except TEST1, default encryption-key reset tool, MySQL utf8mb4 init script.
Expect a 5.1-targeted components release only after the runtime is tagged.

### What this means for the plan

1. Build v1 on **RTE 5.0.2 + CC v5.0.7**; nothing about 5.1 changes that.
2. 5.1 is on the same Spring 6.2 / Boot 3.5 line, so the autoconfigure code should carry over; the
   BOM gets a second coherent pair once a 5.1 components release exists.
3. For a Central target, the eGov Spring forks are the new obstacle: decide early whether to depend
   on them (host-only) or on stock Spring Security 6.5.11 (Central-clean, behaviour unverified).
4. The new AI modules are RTE, not components, and out of this repo's scope unless a future
   component uses them.
