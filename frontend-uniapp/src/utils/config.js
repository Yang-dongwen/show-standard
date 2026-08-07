/**
 * 商家端必须连云 API（产品线 B / SaaS）。
 * 开发可用本机或局域网；正式环境改为 https 合法域名。
 * 禁止对接本地 desktop/SQLite。
 *
 * 切换方式：
 * - H5 开发：空 baseUrl + Vite 代理 /api → 127.0.0.1:8080（避免浏览器 CORS）
 * - 小程序模拟器：http://127.0.0.1:8080
 * - 真机调试：http://你的电脑局域网IP:8080（手机与电脑同网）
 * - 生产：https://你的公网域名（需在微信公众平台配置 request 合法域名）
 */

// #ifdef H5
/** H5 开发走同源相对路径，由 vite server.proxy 转发 /api */
const DEV_BASE = ''
// #endif
// #ifndef H5
const DEV_BASE = 'http://127.0.0.1:8080'
// #endif

/**
 * 生产请改为你的 https 域名，并去掉尾部斜杠。
 * 上线前必须替换；勿将占位域名打进正式包。
 */
const PROD_BASE = 'https://api.example.com'

/**
 * 是否使用生产 baseUrl。
 * 也可在运行时通过 uni.setStorageSync('baseUrlOverride', 'https://...') 临时覆盖。
 */
const USE_PROD = false

export const baseUrl = USE_PROD ? PROD_BASE : DEV_BASE

export const productNote = '需云版 SaaS；本地安装包不使用本客户端'

export default {
  baseUrl,
  productNote,
  USE_PROD,
  PROD_BASE,
  DEV_BASE,
}
