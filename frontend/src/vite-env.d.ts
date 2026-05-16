/// <reference types="vite/client" />

declare module 'remark-gfm' {
  const remarkGfm: any;
  export default remarkGfm;
}

declare module 'sonner' {
  export const toast: {
    success: (message: string) => void;
    error: (message: string) => void;
  };
}
