/**
 * Vue应用入口文件
 */

import Vue from 'vue';
import App from './App.vue';

// 导入Axios配置（会自动注册拦截器）
import './utils/request';

Vue.config.productionTip = false;

new Vue({
  render: (h) => h(App),
}).$mount('#app');
