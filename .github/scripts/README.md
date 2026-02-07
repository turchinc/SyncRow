# Localization Check Workflow

This directory contains scripts and workflows for automated localization checking in the SyncRow project.

## Overview

The localization check workflow automatically:
1. Compares the primary English `strings.xml` with all localized versions
2. Identifies missing translation strings in each locale
3. Creates separate Pull Requests per locale with missing strings
4. Runs automatically on commits to `main` that modify strings, or weekly on Mondays

## Files

### Scripts

- **`check_localizations.py`**: Parses all `strings.xml` files and identifies missing translations
  - Compares primary English (`values/strings.xml`) with localized versions
  - Outputs results to `localization_check_results.json`
  - Exits with error code if any locale has missing strings

- **`create_localization_prs.py`**: Creates Pull Requests for locales with missing strings
  - Reads results from `localization_check_results.json`
  - Creates a separate branch and PR for each locale with missing strings
  - Adds missing strings with English values as placeholders
  - Includes translation instructions in PR description

### Workflow

- **`../workflows/localization-check.yml`**: GitHub Actions workflow
  - **Triggers**:
    - On push to `main` when `strings.xml` files are modified
    - Weekly on Mondays at 9:00 AM UTC
    - Manual trigger via workflow dispatch
  - **Steps**:
    1. Checks out the repository
    2. Sets up Python 3.11
    3. Runs `check_localizations.py`
    4. Uploads results as an artifact
    5. Creates PRs if missing strings are detected
    6. Generates a summary table in the workflow output

## Supported Locales

The workflow checks the following locales (as defined in the SyncRow custom instructions):
- 🇩🇪 German (DE) - `values-de/strings.xml`
- 🇪🇸 Spanish (ES) - `values-es/strings.xml`
- 🇫🇷 French (FR) - `values-fr/strings.xml`
- 🇮🇹 Italian (IT) - `values-it/strings.xml`

## Usage

### Running Locally

To check for missing translations locally:

```bash
# From the repository root
python3 .github/scripts/check_localizations.py
```

This will output results to the console and create `localization_check_results.json`.

### Triggering Manually

You can manually trigger the workflow from GitHub:
1. Go to Actions → Localization Check
2. Click "Run workflow"
3. Select the branch and run

### Understanding PRs

When missing strings are detected, the workflow creates PRs with:
- **Branch name**: `localization/{locale}/missing-strings`
- **Title**: `[i18n] Add missing {LOCALE} translations`
- **Content**: 
  - List of missing string keys
  - Instructions to translate
  - Reminder to use informal address (tu/du/tú)

The strings are added with English values as placeholders. Translators should:
1. Review the PR
2. Translate each string to the target language
3. Ensure informal address is used
4. Update the PR with proper translations

## Customization

To add support for additional locales:
1. Edit `check_localizations.py` and add the locale code to the `locales` list
2. Create the corresponding `values-{locale}/strings.xml` file
3. Run the workflow to check for missing strings

## Troubleshooting

### Script fails to parse XML
- Ensure all `strings.xml` files are valid XML
- Check for unclosed tags or malformed elements

### PRs not being created
- Check that the GitHub token has `contents: write` and `pull-requests: write` permissions
- Verify the GitHub CLI (`gh`) is available in the runner

### Workflow not triggering
- Verify the `paths` filter in the workflow matches your file structure
- Check that changes are being pushed to the `main` branch
