# Package Structure Fix TODO

## Steps:
- [x] 1. Move auth directory to src/main/java/com/theguy/app/auth/ ✓
- [x] 2. Add spring-boot-starter-validation to pom.xml ✓
- [ ] 3. Run `mvn clean compile` to verify build
- [ ] 4. Run `mvn spring-boot:run -Dspring-boot.run.profiles=dev` to test
- [ ] 5. Verify no package/import errors in IDE
- [ ] 6. Test auth endpoints (login/register)

