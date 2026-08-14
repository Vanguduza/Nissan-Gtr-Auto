export type {
  EmailAdapter,
  EmailChannel,
  EmailMessage,
  EmailProviderId,
  EmailSendResult,
} from "./types";
export { providerForChannel } from "./types";
export {
  createResendAdapter,
  type ResendConfig,
} from "./adapters/resend";
export {
  createBrevoAdapter,
  type BrevoConfig,
} from "./adapters/brevo";
