// Prettier 配置：https://prettier.io/docs/options
// 所有代码格式化规则集中在此，ESLint 通过 eslint-plugin-prettier 调用本配置
module.exports = {
  // 每行最大宽度，超过会自动换行（默认 80）
  printWidth: 80,

  // 缩进列数（配合 useTabs 决定用空格还是 Tab）
  tabWidth: 2,

  // false=用空格缩进；true=用 Tab 缩进（与 .editorconfig 的 indent_style 保持一致）
  useTabs: false,

  // true=语句末尾加分号；false=不加（JSX/TS 中通常保留以避免 ASI 风险）
  semi: true,

  // true=字符串使用单引号；false=使用双引号
  singleQuote: true,

  // 尾逗号策略：'none' | 'es5' | 'all'。'all' 会给对象/数组/函数参数最后一项也加逗号，便于 git diff
  trailingComma: 'all',

  // 对象字面量花括号内侧是否加空格：{ foo: 1 } vs {foo: 1}
  bracketSpacing: true,

  // 多行 JSX 中 > 是否单独成行放在最后一行末尾（false=单独一行；true=跟在最后一个属性后）
  bracketSameLine: false,

  // JSX 中字符串使用单引号还是双引号（false=双引号，与 HTML 惯例一致）
  jsxSingleQuote: false,

  // 箭头函数参数括号：'always'=总是加括号 (x) => x；'avoid'=单参数省略括号 x => x
  arrowParens: 'always',

  // 换行符类型：'lf' | 'crlf' | 'cr' | 'auto'。统一为 lf 避免跨平台 git 冲突
  endOfLine: 'lf',

  // 是否格式化嵌入的其他语言（如 markdown 中的代码块、HTML 中的 <script>）
  // 'auto'=自动格式化
  embeddedLanguageFormatting: 'auto',
};
