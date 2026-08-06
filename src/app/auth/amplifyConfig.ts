/**
 * Amplify Auth configuration from build-time environment variables.
 *
 * Exported as a plain object so that both the AuthProvider and any future utility
 * that needs to know "is Cognito active?" can check without duplicating the env reads.
 */

export const COGNITO_USER_POOL_ID = import.meta.env.VITE_COGNITO_USER_POOL_ID ?? "";
export const COGNITO_CLIENT_ID = import.meta.env.VITE_COGNITO_CLIENT_ID ?? "";
export const COGNITO_REGION = import.meta.env.VITE_COGNITO_REGION ?? "us-east-1";

/**
 * True when both the user pool and client id are present at build time.
 * A production build without these values falls through to dev mode (email header auth).
 */
export const cognitoConfigured =
  COGNITO_USER_POOL_ID.length > 0 && COGNITO_CLIENT_ID.length > 0;

/**
 * The Amplify `ResourcesConfig` shape expected by `Amplify.configure()`.
 * Only meaningful when `cognitoConfigured` is true.
 */
export const amplifyAuthConfig = {
  Auth: {
    Cognito: {
      userPoolId: COGNITO_USER_POOL_ID,
      userPoolClientId: COGNITO_CLIENT_ID,
      ...(COGNITO_REGION ? { region: COGNITO_REGION } : {}),
    },
  },
} as const;
