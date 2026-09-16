import { defineConfig, loadEnv } from 'vite'
import path from 'path'
import createVitePlugins from './vite/plugins'

const baseUrl = 'http://localhost:8080' // 后端接口
const clusterNodePortHost = 'http://172.20.211.23'

// https://vitejs.dev/config/
export default defineConfig(({ mode, command }) => {
  const env = loadEnv(mode, process.cwd())
  const { VITE_APP_ENV } = env
  const serviceProxies = {
    '/fep-acc-server': `${clusterNodePortHost}:30030`,
    '/fep-acc': `${clusterNodePortHost}:30030`,
    '/collect-pay-server': `http://127.0.0.1:58101`,
    '/collect-pay': `http://127.0.0.1:58101`,
    '/face-pay-server': `${clusterNodePortHost}:30025`,
    '/fep-app-server': `${clusterNodePortHost}:30010`,
    '/fep-app': `${clusterNodePortHost}:30010`,
    '/security-server': `${clusterNodePortHost}:30012`,
    '/para-server': `${clusterNodePortHost}:30026`,
    '/ticket-server': `${clusterNodePortHost}:30014`,
    '/account-server': `${clusterNodePortHost}:30013`,
    '/account': `${clusterNodePortHost}:30013`,
    '/fep-dev-server': `${clusterNodePortHost}:30009`,
    '/key-server': `${clusterNodePortHost}:30015`,
    '/pay-sign-server': `${clusterNodePortHost}:30016`,
    '/es-server': `${clusterNodePortHost}:30011`,
    '/fep-alipay-server': `${clusterNodePortHost}:30023`,
    '/alipay-account-server': `${clusterNodePortHost}:30021`,
    '/blacklist-server': `${clusterNodePortHost}:30017`,
    '/industry-data-server': `${clusterNodePortHost}:30018`,
    '/daily-ticket-server': `${clusterNodePortHost}:30027`,
    '/gate-txn-pay-server': `${clusterNodePortHost}:30019`,
    '/fep-alipay': `${clusterNodePortHost}:30020`,
    '/alipay-pay-sign-server': `${clusterNodePortHost}:30022`,
    '/web-server': `${clusterNodePortHost}:30028`,
    '/web-admin': `${clusterNodePortHost}:30028`,
    '/card-pool-server': `${clusterNodePortHost}:30033`
  }
  const serviceProxyConfig = Object.fromEntries(
    Object.entries(serviceProxies).map(([context, target]) => [context, {
      target,
      changeOrigin: true,
      rewrite: (path) => path.replace(new RegExp(`^${context}`), '')
    }])
  )
  return {
    // 部署生产环境和开发环境下的URL。
    // 默认情况下，vite 会假设你的应用是被部署在一个域名的根路径上
    // 例如 https://www.ruoyi.vip/。如果应用被部署在一个子路径上，你就需要用这个选项指定这个子路径。例如，如果你的应用被部署在 https://www.ruoyi.vip/admin/，则设置 baseUrl 为 /admin/。
    base: VITE_APP_ENV === 'production' ? '/' : '/',
    plugins: createVitePlugins(env, command === 'build'),
    resolve: {
      // https://cn.vitejs.dev/config/#resolve-alias
      alias: {
        // 设置路径
        '~': path.resolve(__dirname, './'),
        // 设置别名
        '@': path.resolve(__dirname, './src')
      },
      // https://cn.vitejs.dev/config/#resolve-extensions
      extensions: ['.mjs', '.js', '.ts', '.jsx', '.tsx', '.json', '.vue']
    },
    // 打包配置
    build: {
      // https://vite.dev/config/build-options.html
      sourcemap: command === 'build' ? false : 'inline',
      outDir: 'dist',
      assetsDir: 'assets',
      chunkSizeWarningLimit: 2000,
      rollupOptions: {
        output: {
          chunkFileNames: 'static/js/[name]-[hash].js',
          entryFileNames: 'static/js/[name]-[hash].js',
          assetFileNames: 'static/[ext]/[name]-[hash].[ext]'
        }
      }
    },
    // vite 相关配置
    server: {
      port: 80,
      host: true,
      open: true,
      proxy: {
        // 页面请求统一带 /{服务名} 前缀，转发时删除此前缀。
        ...serviceProxyConfig,
         // springdoc proxy
         '^/v3/api-docs/(.*)': {
          target: baseUrl,
          changeOrigin: true,
        }
      }
    },
    css: {
      postcss: {
        plugins: [
          {
            postcssPlugin: 'internal:charset-removal',
            AtRule: {
              charset: (atRule) => {
                if (atRule.name === 'charset') {
                  atRule.remove()
                }
              }
            }
          }
        ]
      }
    }
  }
})
