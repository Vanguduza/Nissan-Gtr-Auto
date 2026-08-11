export type {
  EmailAdapter,
  EmailChannel,
  EmailMessage,
  EmailProviderId,
  EmailSendResult,
} from "./types.ts";
export { providerForChannel } from "./types.ts";
export {
  createResendAdapter,
  type ResendConfig,
} from "./adapters/resend.ts";
export {
  createBrevoAdapter,
  type BrevoConfig,
} from "./adapters/brevo.ts";
