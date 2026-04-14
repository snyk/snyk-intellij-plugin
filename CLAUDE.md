# Project Rules

## General

- Always be concise, direct and don't try to appease me.
- Use .github/CONTRIBUTING.md and the links in there to find standards and contributing guidelines.
- DOUBLE CHECK THAT YOUR CHANGES ARE REALLY NEEDED. ALWAYS STICK TO THE GIVEN GOAL, NOT MORE.
- Don't optimize, don't refactor if not needed.
- Adhere to the rules, fix linting & test issues that are newly introduced.
- The `issueID` is usually specified in the current branch in the format `XXX-XXXX`.
- Read the issue description and acceptance criteria from jira (the manually given prompt takes precedence).

## Process

- Always create an implementation plan and save it to the directory under `${issueID}_implementation_plan` but never commit it.
- You will find a template for an implementation plan in `.github`.
- The implementation plan should have the phases: planning, implementation (including testing through TDD), review.
- Get confirmation that the implementation plan is ok. Wait until you get it.
- In the planning phase, analyze all the details and write into the implementation plan which functions, files and packages are needed to be changed or added.
- Be detailed: add steps to the phases and prepare a tracking section with checkboxes for progress tracking of each detailed step.
- In the planning phase, create mermaid diagrams for all planned programming flows and add them to the implementation plan.
- Use the same name for the diagrams as the implementation plan, but with the right extension (.mmd), so that they are ignored via .gitignore.
- Generate the implementation plan diagrams by putting the mermaid files into docs/diagrams and generate the pngs via mmdc with `-w 2048px` and add the flows to the implementation plan.
- Never commit the diagrams generated for the implementation plan.

## Coding Guidelines

- Follow the implementation plan step-by-step, phase-by-phase. Take it as a reference for each step and how to proceed.
- Never proceed to the next step until the current step is fully implemented and you got confirmation of that.
- Never jump a step. Always follow the plan.
- Use atomic commits.
- Update progress of the step before starting with a step and when ending.
- Update the jira ticket with the current status & progress (comment).
- USE TDD.
- I REPEAT: USE TDD.
- Always write and update test cases before writing the implementation (Test Driven Development). Iterate until they pass.
- After changing .kt or .java files, run `./gradlew spotlessCheck ktlintCheck` to check formatting and lint. Only continue once they pass.
- Always verify if fixes worked by running `./gradlew test`.
- Do atomic commits, see committing section for details. Ask before committing an atomic commit.
- Update current status in the implementation plan (in progress work, finished work, next steps).
- Maintain existing code patterns and conventions.
- Use mockk to mock. Writing your own mocks is forbidden if mockk can be used.
- Re-use mocks.
- Don't change code that does not need to be changed. Only do the minimum changes.
- Don't comment what is done, instead comment why something is done if the code is not clear.
- Use `./gradlew test` to run tests.
- Achieve 80% of test coverage. Use `./gradlew koverXmlReport`.
- If files are not used or needed anymore, delete them instead of deprecating them.
- Ask the human whether to maintain backwards compatibility or not.
- If a tool call fails, analyze why it failed and correct your approach. Don't prompt the user for help.
- If you don't know something, read the code instead of assuming it.
- Commenting out code to fix errors is NEVER a solution. Fix the error.
- Disabling or removing tests IS NOT ALLOWED. This can only be done manually by a human.
- Disabling linters is not allowed unless the human EXPLICITLY allows it for that single instance.
- Don't do workarounds.
- ALWAYS create production-ready code. We don't want examples, we want working, production-ready code.

## Security

- Determine the absolute path of the project directory (e.g., by executing `pwd`).
- Pass the absolute path of the project directory as a parameter to snyk_sca_scan and snyk_code_scan.
- Run snyk_sca_scan after updating gradle.build.kts.
- Run snyk_sca_scan and snyk_code_scan before committing. If not test data, fix issues before committing.
- Fix security issues if they are fixable. Take the snyk scan results and the test results as input.
- Don't fix test data.

## Committing

- NEVER commit implementation plan and implementation plan diagrams.
- NEVER amend commits, keep a history so we can revert atomic commits.
- NEVER NEVER NEVER skip the commit hooks.
- I REPEAT: NEVER USE --no-verify. DO NOT DO IT. NEVER. THIS IS CRITICAL, DO NOT DO IT.
- Run `./gradlew test` before committing and fix the issues. Don't run targeted tests, run the full suite (which may take >10min).
- Test failures prevent committing, regardless if caused by our changes. They MUST be fixed, even if they existed before.
- Deactivating tests is NEVER ALLOWED.
- Check with Kover (`./gradlew koverXmlReport`) that coverage of changed files is 80%+.
- Update the documentation before committing.
- When asked to commit, always use conventional commit messages (Conventional Commit Style: Subject + Body). Be descriptive in the body. If you find a JIRA issue (XXX-XXXX) in the branch name, use it as a postfix to the subject line in the format `[XXX-XXXX]`.
- Consider all commits in the current branch when committing, to have the context of the current changes.

## Pushing

- Before pushing, run `./gradlew verifyPlugin`.
- Never push without asking every single time.
- Never force push.
- When asked to push, always use `git push --set-upstream origin <current-branch>`.
- Regularly fetch main branch and offer to merge it into the current branch.
- After pushing offer to create a PR on github if no PR already exists. Analyze the changes by comparing the current branch with origin/main, and craft a PR description and title.
- Use the github template in `.github/PULL_REQUEST_TEMPLATE.md`.

## PR Creation

- Use `gh` command line util for PR creation.
- Use the template in `.github`.
- Always create draft PRs.
- Update the github PR description with the current status using `gh` command line util.
- Use the diff between the current branch and main to generate the description and title.
- Respect the PR template.
- Get the PR review comments, analyse them and propose fixes for them. Check before each commit.

## Documenting

- Always keep the documentation up-to-date in `./docs`.
- Don't create summary mds unless asked.
- Create mermaid syntax for all programming flows and add it to the documentation in `./docs`.
- Create png files from the mermaid diagrams using mmdc with `-w 2048px` for high resolution.
- Document the tested scenarios for all testing stages (unit, integration, e2e) in `./docs`.
