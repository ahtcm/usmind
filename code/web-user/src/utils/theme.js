// USMIND 主题模板 (simple-mind-map 主题配置)
// 通过 MindMap.defineTheme(name, config) 注册为可用主题
// 结构参考 simple-mind-map 内置 default 主题; branchColors 为彩虹分支配色顺序
export const THEMES = [
  {
    "name": "晨曦白",
    "key": "usmind-light",
    "branchColors": ["#2FB36B", "#22A7C9", "#F6A609", "#E8684A", "#F08BB4", "#9270CA", "#5B8FF9", "#FF9F43"],
    "config": {
          "paddingX": 18,
          "paddingY": 8,
          "lineWidth": 4,
          "lineColor": "#94a3b8",
          "lineStyle": "curve",
          "rootLineKeepSameInCurve": true,
          "rootLineStartPositionKeepSameInCurve": true,
          "backgroundColor": "#fafbff",
          "root": {
                "shape": "roundedRectangle",
                "fillColor": "#4A5CF7",
                "color": "#ffffff",
                "fontSize": 36,
                "fontWeight": "bold",
                "borderColor": "transparent",
                "borderWidth": 0,
                "borderRadius": 20,
                "paddingX": 44,
                "paddingY": 26
          },
          "second": {
                "shape": "roundedRectangle",
                "fillColor": "#ffffff",
                "color": "#334155",
                "fontSize": 25,
                "fontWeight": "600",
                "borderColor": "#cbd5e1",
                "borderWidth": 1,
                "borderRadius": 16,
                "paddingX": 30,
                "paddingY": 18,
                "marginX": 80,
                "marginY": 72
          },
          "node": {
                "shape": "roundedRectangle",
                "fillColor": "#ffffff",
                "color": "#475569",
                "fontSize": 22,
                "fontWeight": "500",
                "borderColor": "#e2e8f0",
                "borderWidth": 1,
                "borderRadius": 14,
                "paddingX": 24,
                "paddingY": 14,
                "marginX": 60,
                "marginY": 34
          }
    }
  },
  {
    "name": "深空黑",
    "key": "usmind-dark",
    "branchColors": ["#4ADE80", "#22D3EE", "#FACC15", "#FB7185", "#C084FC", "#818CF8", "#F97316", "#2DD4BF"],
    "config": {
          "paddingX": 16,
          "paddingY": 7,
          "lineWidth": 4,
          "lineColor": "#64748b",
          "lineStyle": "curve",
          "rootLineKeepSameInCurve": true,
          "rootLineStartPositionKeepSameInCurve": true,
          "backgroundColor": "#16181d",
          "root": {
                "shape": "roundedRectangle",
                "fillColor": "#3b82f6",
                "color": "#ffffff",
                "fontSize": 36,
                "fontWeight": "bold",
                "borderColor": "transparent",
                "borderWidth": 0,
                "borderRadius": 20,
                "paddingX": 44,
                "paddingY": 26
          },
          "second": {
                "shape": "roundedRectangle",
                "fillColor": "#1e293b",
                "color": "#e2e8f0",
                "fontSize": 25,
                "fontWeight": "600",
                "borderColor": "#334155",
                "borderWidth": 1,
                "borderRadius": 16,
                "paddingX": 30,
                "paddingY": 18,
                "marginX": 80,
                "marginY": 72
          },
          "node": {
                "shape": "roundedRectangle",
                "fillColor": "#0f172a",
                "color": "#cbd5e1",
                "fontSize": 22,
                "fontWeight": "500",
                "borderColor": "#1e293b",
                "borderWidth": 1,
                "borderRadius": 14,
                "paddingX": 24,
                "paddingY": 14,
                "marginX": 60,
                "marginY": 34
          }
    }
  },
  {
    "name": "极简白",
    "key": "usmind-white",
    "branchColors": ["#2FB36B", "#22A7C9", "#F6A609", "#E8684A", "#F08BB4", "#9270CA", "#5B8FF9", "#FF9F43"],
    "config": {
          "paddingX": 16,
          "paddingY": 7,
          "lineWidth": 4,
          "lineColor": "#cbd5e1",
          "lineStyle": "curve",
          "rootLineKeepSameInCurve": true,
          "rootLineStartPositionKeepSameInCurve": true,
          "backgroundColor": "#ffffff",
          "root": {
                "shape": "rectangle",
                "fillColor": "#1e293b",
                "color": "#ffffff",
                "fontSize": 36,
                "fontWeight": "bold",
                "borderColor": "transparent",
                "borderWidth": 0,
                "borderRadius": 20,
                "paddingX": 44,
                "paddingY": 26
          },
          "second": {
                "shape": "rectangle",
                "fillColor": "#f8fafc",
                "color": "#334155",
                "fontSize": 25,
                "fontWeight": "600",
                "borderColor": "#e2e8f0",
                "borderWidth": 1,
                "borderRadius": 16,
                "paddingX": 30,
                "paddingY": 18,
                "marginX": 80,
                "marginY": 72
          },
          "node": {
                "shape": "rectangle",
                "fillColor": "#ffffff",
                "color": "#475569",
                "fontSize": 22,
                "fontWeight": "500",
                "borderColor": "#e2e8f0",
                "borderWidth": 1,
                "borderRadius": 14,
                "paddingX": 24,
                "paddingY": 14,
                "marginX": 60,
                "marginY": 34
          }
    }
  },
  {
    "name": "暮色紫",
    "key": "usmind-purple",
    "branchColors": ["#C084FC", "#F472B6", "#FB923C", "#4ADE80", "#22D3EE", "#FACC15", "#818CF8", "#F87171"],
    "config": {
          "paddingX": 16,
          "paddingY": 7,
          "lineWidth": 4,
          "lineColor": "#8b5cf6",
          "lineStyle": "curve",
          "rootLineKeepSameInCurve": true,
          "rootLineStartPositionKeepSameInCurve": true,
          "backgroundColor": "#1b1620",
          "root": {
                "shape": "roundedRectangle",
                "fillColor": "#7c3aed",
                "color": "#ffffff",
                "fontSize": 36,
                "fontWeight": "bold",
                "borderColor": "transparent",
                "borderWidth": 0,
                "borderRadius": 20,
                "paddingX": 44,
                "paddingY": 26
          },
          "second": {
                "shape": "roundedRectangle",
                "fillColor": "#2a2140",
                "color": "#e9d5ff",
                "fontSize": 25,
                "fontWeight": "600",
                "borderColor": "#4c1d95",
                "borderWidth": 1,
                "borderRadius": 16,
                "paddingX": 30,
                "paddingY": 18,
                "marginX": 80,
                "marginY": 72
          },
          "node": {
                "shape": "roundedRectangle",
                "fillColor": "#221a33",
                "color": "#d8b4fe",
                "fontSize": 22,
                "fontWeight": "500",
                "borderColor": "#4c1d95",
                "borderWidth": 1,
                "borderRadius": 14,
                "paddingX": 24,
                "paddingY": 14,
                "marginX": 60,
                "marginY": 34
          }
    }
  },
  {
    "name": "森林绿",
    "key": "usmind-green",
    "branchColors": ["#4ADE80", "#22D3EE", "#FACC15", "#FB7185", "#C084FC", "#F97316", "#34D399", "#60A5FA"],
    "config": {
          "paddingX": 16,
          "paddingY": 7,
          "lineWidth": 4,
          "lineColor": "#4ade80",
          "lineStyle": "curve",
          "rootLineKeepSameInCurve": true,
          "rootLineStartPositionKeepSameInCurve": true,
          "backgroundColor": "#0f1a12",
          "root": {
                "shape": "roundedRectangle",
                "fillColor": "#22c55e",
                "color": "#052e16",
                "fontSize": 36,
                "fontWeight": "bold",
                "borderColor": "transparent",
                "borderWidth": 0,
                "borderRadius": 20,
                "paddingX": 44,
                "paddingY": 26
          },
          "second": {
                "shape": "roundedRectangle",
                "fillColor": "#14532d",
                "color": "#dcfce7",
                "fontSize": 25,
                "fontWeight": "600",
                "borderColor": "#16a34a",
                "borderWidth": 1,
                "borderRadius": 16,
                "paddingX": 30,
                "paddingY": 18,
                "marginX": 80,
                "marginY": 72
          },
          "node": {
                "shape": "roundedRectangle",
                "fillColor": "#166534",
                "color": "#bbf7d0",
                "fontSize": 22,
                "fontWeight": "500",
                "borderColor": "#16a34a",
                "borderWidth": 1,
                "borderRadius": 14,
                "paddingX": 24,
                "paddingY": 14,
                "marginX": 60,
                "marginY": 34
          }
    }
  },
  {
    "name": "暖阳橙",
    "key": "usmind-orange",
    "branchColors": ["#FB923C", "#F87171", "#FACC15", "#4ADE80", "#22D3EE", "#C084FC", "#FBBF24", "#F472B6"],
    "config": {
          "paddingX": 16,
          "paddingY": 7,
          "lineWidth": 4,
          "lineColor": "#fb923c",
          "lineStyle": "curve",
          "rootLineKeepSameInCurve": true,
          "rootLineStartPositionKeepSameInCurve": true,
          "backgroundColor": "#1a130c",
          "root": {
                "shape": "roundedRectangle",
                "fillColor": "#f97316",
                "color": "#ffffff",
                "fontSize": 36,
                "fontWeight": "bold",
                "borderColor": "transparent",
                "borderWidth": 0,
                "borderRadius": 20,
                "paddingX": 44,
                "paddingY": 26
          },
          "second": {
                "shape": "roundedRectangle",
                "fillColor": "#431407",
                "color": "#fed7aa",
                "fontSize": 25,
                "fontWeight": "600",
                "borderColor": "#ea580c",
                "borderWidth": 1,
                "borderRadius": 16,
                "paddingX": 30,
                "paddingY": 18,
                "marginX": 80,
                "marginY": 72
          },
          "node": {
                "shape": "roundedRectangle",
                "fillColor": "#3b1c06",
                "color": "#fdba74",
                "fontSize": 22,
                "fontWeight": "500",
                "borderColor": "#ea580c",
                "borderWidth": 1,
                "borderRadius": 14,
                "paddingX": 24,
                "paddingY": 14,
                "marginX": 60,
                "marginY": 34
          }
    }
  },
  {
    "name": "黑曜石",
    "key": "usmind-blackgold",
    "branchColors": ["#ffffff", "#E63946", "#F4C542", "#3A86FF", "#4ADE80", "#C084FC", "#F97316", "#22D3EE"],
    "config": {
          "paddingX": 16,
          "paddingY": 7,
          "lineWidth": 4,
          "lineColor": "#666666",
          "lineStyle": "curve",
          "rootLineKeepSameInCurve": true,
          "rootLineStartPositionKeepSameInCurve": true,
          "backgroundColor": "#1a1a1c",
          "root": {
                "shape": "rectangle",
                "fillColor": "#ffffff",
                "color": "#111111",
                "fontSize": 36,
                "fontWeight": "bold",
                "borderColor": "transparent",
                "borderWidth": 0,
                "borderRadius": 20,
                "paddingX": 44,
                "paddingY": 26
          },
          "second": {
                "shape": "rectangle",
                "fillColor": "#2a2a2c",
                "color": "#eeeeee",
                "fontSize": 25,
                "fontWeight": "600",
                "borderColor": "#3a3a3c",
                "borderWidth": 1,
                "borderRadius": 16,
                "paddingX": 30,
                "paddingY": 18,
                "marginX": 80,
                "marginY": 72
          },
          "node": {
                "shape": "rectangle",
                "fillColor": "#232325",
                "color": "#dddddd",
                "fontSize": 22,
                "fontWeight": "500",
                "borderColor": "#333333",
                "borderWidth": 1,
                "borderRadius": 14,
                "paddingX": 24,
                "paddingY": 14,
                "marginX": 60,
                "marginY": 34
          }
    }
  }
]

// 兼容旧代码的渲染函数(保留导出, 实际不再使用)
export function renderThemeList() { /* mind-map 主题由 main.js 渲染 */ }

