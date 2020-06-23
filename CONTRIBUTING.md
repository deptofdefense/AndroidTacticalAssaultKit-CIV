# Contributing to AndroidTacticalAssaultKit-CIV (ATAK-CIV)

Anyone is welcome to contribute code changes and additions to this project. If you'd like your changes merged into the master branch, please read the following document before opening a [pull request][pulls].

There are several ways in which you can help improve this project:

1. Fix an existing [issue][issues] and submit a [pull request][pulls].
1. Review open [pull requests][pulls].
1. Report a new [issue][issues]. _Only do this after you've made sure the behavior or problem you're observing isn't already documented in an open issue._

## Table of Contents

- [Getting Started](#getting-started)
- [Making Changes](#making-changes)
- [Code Style](#code-style)
- [Legalese](#legalese)

## Getting Started

ATAK-CIV is a andorid application and software development kit. 

## Making Changes

1. Fork and clone the project's repo.
1. Install development dependencies as in the BUILDING.md document.
1. Create a feature branch for the code changes you're looking to make: `git checkout -b your-descriptive-branch-name origin/master`.
1. _Write some code!_
1. Run the application and verify that your changes function as intended: `gradlew installCivDebug` and `gradlew installCivRelease` then launch the APK for the respective results.
1. If your changes would benefit from testing, add the necessary tests and verify everything passes by running the Test Report Task, e.g. `gradlew -Pcoverage createCivDebugJacocoTestReport`.
1. Commit your changes: `git commit -am 'Add some new feature or fix some issue'`. _(See [this excellent article](https://chris.beams.io/posts/git-commit) for tips on writing useful Git commit messages.)_
1. Push the branch to your fork: `git push -u origin your-descriptive-branch-name`.
1. Create a new [pull request][pulls] and we'll review your changes.

## Code Style

ATAK makes use of the supplied `android-formatting.xml` file

1. Go to the Plugins Section of Android Studio Settings
1. Click on Browse Repositories.
1. Type Eclipse Code Formatter in the search box.
1. Install and restart Android Studio
1. Go to the Plugins Section of Android Studio Settings
1. Select Elipse Code Formatter
1. Then select the Code Format XML file.
1. Click OK

## Legalese

Before submitting a pull request to this repository for the first time, you'll need to sign a [Developer Certificate of Origin](https://developercertificate.org) (DCO). To read and agree to the DCO, you'll add your name and email address to [`CONTRIBUTORS.md`][contributors]. At a high level, this tells us that you have the right to submit the work you're contributing in your pull request and says that you consent to us treating the contribution in a way consistent with the license associated with this software (as described in [`LICENSE.md`][license]) and its documentation ("Project").

You may submit contributions anonymously or under a pseudonym if you'd like, but we need to be able to reach you at the email address you provide when agreeing to the DCO. Contributions you make to this public Department of Defense repository are completely voluntary. When you submit a pull request, you're offering your contribution without expectation of payment and you expressly waive any future pay claims against the U.S. Federal Government related to your contribution.

[contributors]: https://github.com/deptofdefense/dds.mil/blob/master/CONTRIBUTORS.md
[gemfile]: https://github.com/deptofdefense/dds.mil/blob/master/Gemfile
[issues]: https://github.com/deptofdefense/dds.mil/issues
[license]: https://github.com/deptofdefense/dds.mil/blob/master/LICENSE.md
[pulls]: https://github.com/deptofdefense/dds.mil/pulls
