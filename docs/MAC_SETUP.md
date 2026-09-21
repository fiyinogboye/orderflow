# Mac setup, one step at a time

## You already have Maven

Open the extracted `orderflow` folder in VS Code. Choose Terminal → New Terminal. Run:

```bash
pwd
ls
mvn -version
```

You should see `pom.xml` in the file list. If you see only another `orderflow` folder, open that inner folder instead.

Then:

```bash
mvn spring-boot:run
```

The app is ready when the log says `Started OrderFlowApplication`. Open http://localhost:8081 and use student / learn1234. Do not use OpsHub's login details.

## Java version

The build targets Java 17. Check the installed JDKs on a Mac:

```bash
/usr/libexec/java_home -V
```

If Java 17 is listed, select it in this terminal:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
export PATH="$JAVA_HOME/bin:$PATH"
mvn -version
```

If it is not listed and your Java 25 build fails, install a JDK 17 package from a trusted Java distributor such as Eclipse Temurin. Your known Mac architecture is **Intel / x64**, not Apple Silicon / aarch64. The Docker setup also uses JDK 17 without changing your Mac's system Java.

## Common problems

| What you see | What to check |
| --- | --- |
| `mvn: command not found` | Open the terminal where Maven worked before, or use `/Users/macuser/apache-maven-3.9.11/bin/mvn spring-boot:run` if that is still your Maven location |
| No `pom.xml` | The terminal is in the wrong folder |
| Port 8081 already in use | Stop the other OrderFlow instance with Control+C, or run `mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8082` and open 8082 |
| Login rejected | Use student / learn1234; check whether you set DEMO_PASSWORD |
| Key was used with different details | Click New key before placing a different order |
| Not enough stock | Pick another product; use operator cancellation for DEAD orders; paid purchases do not automatically restock |
| Orders disappear when switching to Docker | H2 and PostgreSQL are separate databases |
| Database already in use | Stop the other local process using the same `data` directory |
| Flyway checksum mismatch | Restore a migration you changed; add a new migration for a new schema change |
| Docker command unavailable | Stay in local Maven mode until Docker Desktop is installed and running |

## Put your version on GitHub later

Use GitHub Desktop as you did with OpsHub. Create a separate repository for this project. Check the changed-files list before publishing: source and docs should be included, but `data/`, `target/`, `.env`, and logs should not. The included `.gitignore` handles those folders.

Start with a small change you understand, then commit it with a plain description of what changed. Keep a short learning note with that commit. There is no need to rename or rewrite everything on the first day.
