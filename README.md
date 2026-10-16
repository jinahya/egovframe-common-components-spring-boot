# egovframe-common-components-spring-boot

eGovFrame (전자정부 표준프레임워크) **common components, headless, for Spring Boot.**

Upstream ships the common components only as WAR-packaged source that you copy into your project
with the eGovFrame IDE. This repository compiles that source, unmodified, into a jar, leaves the
JSP/controller half behind, and adds the Spring Boot wiring the copy-the-source model never needed:
a one-time bridge to the eGovFrame runtime, and one property per component.

This is an independent repackaging. It is not published or endorsed by the Ministry of the Interior
and Safety (행정안전부), the National Information Society Agency (한국지능정보사회진흥원, NIA), or the
eGovFrame Open Community. See [License](#license).

**Status:** builds and passes its tests; not yet published to any repository. See
[What is verified](#what-is-verified) and [Not done](#not-done).

## Using it

```xml
<dependency>
  <groupId>com.github.jinahya</groupId>
  <artifactId>egovframe-common-components-spring-boot-starter</artifactId>
  <version>0.0.1-SNAPSHOT</version>
</dependency>
```

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost/egov   # your DataSource; upstream's tables must exist in it
egovframe:
  database: postgres        # which mapper set to load: postgres mysql maria oracle tibero altibase cubrid goldilocks
  components:
    sym.log.clg:
      enabled: true         # 접속로그관리: EgovLoginLogService, loginLogDAO, egovLoginLogIdGnrService
    sec.ram:
      enabled: true         # 권한관리
```

Then inject upstream's services and DAOs as ordinary beans:

```java
@Resource(name = "EgovLoginLogService") EgovLoginLogService loginLogService;
```

Every component is off unless its property is set. A component brings its own services and DAOs,
the services and DAOs it injects by name from other components, their mapper XML, and the
ID-generation beans it uses. Nothing else is in the context. The full list, with what each one
pulls in and what it still needs from you, is [docs/COMPONENTS.md](docs/COMPONENTS.md).

The schema is yours to create: `egovframe-common-components-schema` carries upstream's DDL, DML
and comment scripts for all eight databases under `egovframe/common-components/script/`, verbatim.
Upstream's DDL is not re-runnable.

### What the core gives you without any component

`EgovFrameAutoConfiguration` is on whenever the runtime is on the classpath, and defines the beans
upstream's code injects by name, each backing off to one you define under the same name:

| Bean | What it is |
|---|---|
| `egov.dataSource`, `txManager` | aliases of Boot's single `DataSource` and `PlatformTransactionManager` |
| `egov.sqlSession`, `egov.sqlSessionTemplate` | one MyBatis factory over the mapper XML of the enabled components, for `egovframe.database` |
| `messageSource` | the runtime's bundles (ID generation, properties), upstream's `message-common`/`message-validation`, plus `spring.messages.basename`; the runtime looks this name up itself, so Boot's own backs off |
| `egovMessageSource`, `leaveaTrace`, `propertiesService`, `egovLoginConfig`, `egov.lobHandler`, three whitelists | as upstream's `context-*.xml` define them |
| `egovUserDetailsHelper` | wired when an `EgovUserDetailsService` bean exists; `egovframe.user-details=dummy|session|security` creates upstream's |
| `egov.transactionAdvisor` | upstream's declarative transaction on `egovframework.com..*Impl.*(..)`, rollback on any `Exception`; `egovframe.transaction.enabled=false` removes it |

Properties: `egovframe.database`, `egovframe.user-details`, `egovframe.transaction.enabled`,
`egovframe.login.lock`, `egovframe.login.lock-count`, `egovframe.whitelist.*`,
`egovframe.properties.*` (extra entries of `propertiesService`), `egovframe.globals.materialize`,
and `egovframe.components.<package>.enabled`. All are in the configuration metadata.

### Two things upstream does that you should know

- **`egovProps/globals.properties` in the working directory.** Upstream's static `EgovProperties`
  and `Globals` read that file from disk, not from the classpath, and many services touch `Globals`
  during class initialization. On startup the core copies upstream's bundled `egovProps/` (the
  properties and the monitoring scripts) into the working directory if it is not there, never
  overwriting; edit the copy. `egovframe.globals.materialize=false` turns that off.
- **The components jar lacks one directory entry**, `egovframework/com/cmm/service/`. That is what
  makes upstream fall back to the working directory above instead of failing on a `jar:` URL. Every
  other directory entry is present, so `classpath*:` scanning into the jar works.

## Building

Needs a JDK of 17 or newer to run Maven (the project is built on 25) and Maven 3.9 or newer.
Every artifact is compiled to Java 17.

```sh
git clone --recurse-submodules https://github.com/jinahya/egovframe-common-components-spring-boot
cd egovframe-common-components-spring-boot
mvn install
```

Nothing is fetched from the eGovFrame host: the runtime jars, their parent POMs and
`sndng-mail` are in `repository/`, a git-managed Maven repository that the root pom lists first
(`maven.egovframe.go.kr` is the fallback). The upstream source is the submodule
`upstream/egovframe-common-components`, pinned at a tag and compiled in place; nothing in it is
edited. Docker, if present, runs the PostgreSQL sweep; without it that one test is skipped.

### Layout

```
pom.xml                                            parent: versions, repositories, plugin versions
egovframe-common-components/                       upstream's src/main/java + resources as a jar; version = upstream tag
egovframe-common-components-schema/                upstream's script/ as classpath resources; version = upstream tag
egovframe-common-components-spring-boot-autoconfigure/
  .../autoconfigure/EgovFrameAutoConfiguration     the core bridge
  .../autoconfigure/components/*Configuration      GENERATED, one per component; do not edit
egovframe-common-components-spring-boot-starter/   what an application depends on
upstream/egovframe-common-components               git submodule, tag v5.0.7
repository/                                        mirrored eGovFrame artifacts (jar, pom, sources, sha1)
mirror/pom.xml                                     refills repository/ from the host; run by hand, not a module
tools/generate_components.py                       derives the component configurations and docs/COMPONENTS.md
tools/StripJarEntries.java                         removes the one directory entry at package time
docs/COMPONENTS.md                                 GENERATED catalog
docs/RESEARCH.md                                   the research notes this was built from
```

### How the component configurations are derived

`tools/generate_components.py` reads the submodule and writes one class per upstream package that
has a `service/impl` subpackage (134 of them; `uss.ion.tir`, Twitter, is excluded because its
client has no implementation). For each it computes a closure over:

0. constructor parameters, Lombok `@RequiredArgsConstructor` final fields and `@Autowired` fields, by type;
1. `@Resource(name = ...)`, by name, resolved to the `@Service`/`@Repository`/`@Component` of that
   name, a `@Bean` method, an ID-generation XML (`context-idgn-*.xml`) defining it, or a bean the
   core provides;
2. the mapper directories of every package the closure touches.

Classes in `.web.` packages, `*Scheduling`, and the password resolver are never imported. Names
that resolve to nothing are listed on the class and in the catalog as what the application must
provide. Rerun it after moving the submodule:

```sh
python3 -I tools/generate_components.py
```

## What is verified

`mvn install` runs, on an empty H2 and the PostgreSQL mapper set:

- the core alone, with and without a DataSource, and application beans winning over it;
- each of the 134 components alone: 126 boot, the 8 that need an application-provided bean or
  library fail naming exactly it (`cop.ems`, `ext.ldapumt`, `sym.ccm.zip`, `uat.uia`, `cop.sms`, ...);
- all 126 self-contained components in one context: 757 beans, 138 DAOs, 138 mapper namespaces,
  1,133 statements, no duplicate names, every mapper parsed;
- the starter in a plain `@SpringBootApplication` with Logback as the only logging binding;

and with Docker, upstream's PostgreSQL DDL in a real PostgreSQL, every `<select>` of those 126
components executed with upstream's paging parameters:

| Selects | Missing table (this project's defect) | Missing column (upstream SQL vs upstream DDL) | Other (null parameter typing in the sweep) |
|---|---|---|---|
| 673 | 0 | 8 | 29 |

The eight are printed by the test: `statsdate`, `reg_date`, `adbk_nm`, `qustnr_tmplat_ty` in the
statistics, address-book and questionnaire mappers.

## Versions

| This project | Components tag (submodule) | Parent POM | Runtime (RTE) | Spring Boot | Java |
|---|---|---|---|---|---|
| 0.0.1-SNAPSHOT | v5.0.7 (2026-10-07) | 5.0.2 | 5.0.2 | 3.5.16 | 17 (built on 25) |

The components tag decides the runtime: its pom names `egovframe-web-config-parent`, and that
parent fixes `egovframe.rte.version`. At v5.0.7 nothing is pinned otherwise (v5.0.6 pinned
`fdl-security` 5.0.1). Boot 3.5 is the line whose Spring Framework 6.2 and Security 6.5 the
runtime 5.0.x is compiled against; Boot 4 is untested with it.

### Moving to a new upstream tag

1. `cd upstream/egovframe-common-components && git fetch --tags && git checkout vX.Y.Z`, then read the
   `<parent>` version in its `pom.xml` and look for any `<version>` on an `org.egovframe.rte` dependency.
2. In the root `pom.xml` set `egovframe.common-components.version`, `egovframe.web.version`,
   `egovframe.rte.version`; set the same version in `egovframe-common-components/pom.xml` and
   `egovframe-common-components-schema/pom.xml` (a module version cannot be a property).
3. If the runtime version changed: `mvn -f mirror/pom.xml`, delete the `_remote.repositories` and
   `maven-metadata-local.xml` files it leaves in `repository/`, write a `.sha1` beside every jar and pom
   (`shasum -a 1`), commit `repository/`.
4. Diff upstream's `<dependencies>` against `egovframe-common-components/pom.xml`.
5. `python3 -I tools/generate_components.py`, then `mvn install`. The tests say what changed.

## Not done

- **Not published.** Decide between Maven Central, which refuses poms with extra repositories and
  dependencies not on Central and so forces republishing the 24 runtime jars under this groupId, and
  GitHub Packages or a private repository, where the eGovFrame host stays the source of the runtime.
- **Upstream's AOP contexts** (`context-syslogaop.xml`, `context-privacylogaop.xml`,
  `context-aspect.xml`'s exception transfer) are not reproduced. The system-log and privacy-log
  aspects would wrap every service method; make them opt-in features when someone needs them.
- **Components needing application beans** stay as they are: `cop.ems` needs `egovMultiPartEmail`,
  `ext.ldapumt` needs `ldapTemplate`, `sym.ccm.zip` the Excel zip services, `uat.uia`
  `egovSecurityConfig`, `cop.sms` the proprietary `smeapi` jar. The catalog lists them.
- **The sweep runs on PostgreSQL only.** MySQL, MariaDB, Oracle and CUBRID have public images and
  could be added the same way; Tibero, Altibase and Goldilocks have none and their drivers are
  proprietary, so their mapper sets are only ever parsed.
- **No BOM module** yet; the versions live in the root pom.

## License

Code in this repository: Apache License 2.0. Upstream eGovFrame runtime and common components:
Apache License 2.0, Copyright MOPAS/MOIS and The eGovFrame Open Community, redistributed here
unmodified as an independent repackaging; the jars carry upstream's LICENSE and a NOTICE. The
third-party jars found in upstream's `WEB-INF/lib` (SMS, PKI, OnePass, vendor JDBC drivers) are
**not** redistributed, and `sndng-mail` is mirrored for the build only.
