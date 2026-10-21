/// <reference types="vite/client" />

// 本次修改：让独立 TypeScript 校验能够识别 Vue 单文件组件入口。
declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<Record<string, unknown>, Record<string, unknown>, unknown>
  export default component
}
