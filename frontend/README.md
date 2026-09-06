# 前端开发说明：React + TypeScript + Vite

本文保留前端初始模板的开发说明，介绍 React、TypeScript、Vite 的基础配置、热模块替换（HMR）和 ESLint 扩展方式。以下可选配置不是学校生产部署步骤，也不表示项目已经启用了所有插件。正式部署请阅读[学校部署指南](../docs/SCHOOL_DEPLOYMENT.md)。

模板列出的两种官方 React 插件如下：

- [@vitejs/plugin-react](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react) 使用 [Babel](https://babeljs.io/)（或在使用 [rolldown-vite](https://vite.dev/guide/rolldown) 时使用 [oxc](https://oxc.rs)）实现快速刷新（Fast Refresh）
- [@vitejs/plugin-react-swc](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react-swc) 使用 [SWC](https://swc.rs/) 实现快速刷新

## React 编译器

初始模板未启用 React 编译器，以避免其对开发和构建性能的影响。若计划启用，请先阅读[安装说明](https://react.dev/learn/react-compiler/installation)，并按正常开发与测试流程评估。

## 扩展 ESLint 配置

如需增强代码静态检查，可考虑启用能够读取 TypeScript 类型信息的规则。下面只是可选配置示例，使用前须结合现有项目配置审查：

```js
export default defineConfig([
  globalIgnores(['dist']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      // 其他配置……

      // 移除 tseslint.configs.recommended，改用下面这一项
      tseslint.configs.recommendedTypeChecked,
      // 也可选择下面的配置，启用更严格的规则
      tseslint.configs.strictTypeChecked,
      // 如需代码风格规则，可额外加入下面这一项
      tseslint.configs.stylisticTypeChecked,

      // 其他配置……
    ],
    languageOptions: {
      parserOptions: {
        project: ['./tsconfig.node.json', './tsconfig.app.json'],
        tsconfigRootDir: import.meta.dirname,
      },
      // 其他选项……
    },
  },
])
```

也可安装 [eslint-plugin-react-x](https://github.com/Rel1cx/eslint-react/tree/main/packages/plugins/eslint-plugin-react-x) 和 [eslint-plugin-react-dom](https://github.com/Rel1cx/eslint-react/tree/main/packages/plugins/eslint-plugin-react-dom)，以使用针对 React 的检查规则：

```js
// eslint.config.js
import reactX from 'eslint-plugin-react-x'
import reactDom from 'eslint-plugin-react-dom'

export default defineConfig([
  globalIgnores(['dist']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      // 其他配置……
      // 启用 React 检查规则
      reactX.configs['recommended-typescript'],
      // 启用 React DOM 检查规则
      reactDom.configs.recommended,
    ],
    languageOptions: {
      parserOptions: {
        project: ['./tsconfig.node.json', './tsconfig.app.json'],
        tsconfigRootDir: import.meta.dirname,
      },
      // 其他选项……
    },
  },
])
```
