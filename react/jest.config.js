/** @type {import('jest').Config} */
module.exports = {
  preset: 'ts-jest',
  testEnvironment: 'node',
  testMatch: ['**/__tests__/**/*.test.ts'],
  moduleNameMapper: {
    '^expo-clipboard$': '<rootDir>/__mocks__/expoClipboardMock.ts'
  }
};
