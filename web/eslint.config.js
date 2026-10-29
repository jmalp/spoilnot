// Lint for the web app (DESIGN 18.3). Components and screens never import Firebase: only src/data does.
import js from '@eslint/js';
import globals from 'globals';
import reactHooks from 'eslint-plugin-react-hooks';
import tseslint from 'typescript-eslint';

export default tseslint.config(
  { ignores: ['dist', 'dev-dist', 'playwright-report', 'test-results', '**/*.generated.ts'] },
  js.configs.recommended,
  ...tseslint.configs.strict,
  reactHooks.configs.flat['recommended-latest'],
  {
    languageOptions: { globals: { ...globals.browser, ...globals.node } },
    rules: {
      // With noUncheckedIndexedAccess, `!` marks reads the code knows are there (Kotlin's `!!`).
      '@typescript-eslint/no-non-null-assertion': 'off',
      'no-restricted-imports': [
        'error',
        {
          patterns: [
            { group: ['firebase/*', '@firebase/*'], message: 'Only src/data may use Firebase (DESIGN 18.3).' },
          ],
        },
      ],
    },
  },
  {
    files: ['src/data/**', 'src/sw.ts'],
    rules: { 'no-restricted-imports': 'off' },
  },
);
