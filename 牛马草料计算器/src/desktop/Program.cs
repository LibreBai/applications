// 牛马草料计算器 · 桌面版 v0.7
// 原生 WinForms 实现，零第三方依赖（Windows 自带 .NET Framework 4.8 即可编译运行）
// v0.7 布局引擎：全界面按"比例坐标"呈现——每个控件的位置/尺寸都以父容器尺寸的百分比记录，
//   窗口缩放/最大化/DPI 变化时按同一比例系整体重排，代码中不写死任何绝对布局坐标；
//   拉伸窗口时锁定设计纵横比（480:788），另一维自动联动；文字标签 AutoSize 永不裁切。
// 编译：见 build.bat
using System;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Globalization;
using System.IO;
using System.Runtime.InteropServices;
using System.Windows.Forms;

namespace NiuMaIncome
{
    static class Program
    {
        [DllImport("user32.dll")]
        private static extern bool SetProcessDPIAware();

        [STAThread]
        static void Main()
        {
            try { SetProcessDPIAware(); } catch { }
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            Application.Run(new MainForm());
        }
    }

    static class Ui
    {
        // 设计基准画布（比例坐标的参照系）
        public const double DesignW = 480.0, DesignH = 800.0;

        public static readonly Color Text = Color.FromArgb(236, 240, 248);
        public static readonly Color Soft = Color.FromArgb(201, 210, 228);
        public static readonly Color Muted = Color.FromArgb(158, 168, 192);
        public static readonly Color Faint = Color.FromArgb(112, 121, 142);
        public static readonly Color Green = Color.FromArgb(52, 211, 153);
        public static readonly Color Gold = Color.FromArgb(251, 191, 36);
        public static readonly Color Cyan = Color.FromArgb(125, 211, 252);
        public static readonly Color Card = Color.FromArgb(18, 24, 38);
        public static readonly Color Input = Color.FromArgb(13, 19, 34);
        public static readonly Color Line = Color.FromArgb(44, 57, 88);

        public static GraphicsPath Round(Rectangle r, int rad)
        {
            var g = new GraphicsPath();
            int d = rad * 2;
            if (d > r.Width) d = r.Width;
            if (d > r.Height) d = r.Height;
            g.AddArc(r.X, r.Y, d, d, 180, 90);
            g.AddArc(r.Right - d - 1, r.Y, d, d, 270, 90);
            g.AddArc(r.Right - d - 1, r.Bottom - d - 1, d, d, 0, 90);
            g.AddArc(r.X, r.Bottom - d - 1, d, d, 90, 90);
            g.CloseFigure();
            return g;
        }
    }

    // 暗色圆角卡片容器
    class Card : Panel
    {
        public Card()
        {
            DoubleBuffered = true;
            BackColor = Ui.Card;
        }
        protected override void OnPaint(PaintEventArgs e)
        {
            base.OnPaint(e);
            using (var p = new Pen(Ui.Line, 1f))
            using (var path = Ui.Round(ClientRectangle, 14))
                e.Graphics.DrawPath(p, path);
        }
    }

    // 自绘进度条
    class Bar : Control
    {
        public Color Fill1 = Color.FromArgb(16, 185, 129);
        public Color Fill2 = Color.FromArgb(110, 231, 187);
        public double Value; // 0-100

        public Bar()
        {
            DoubleBuffered = true;
            BackColor = Ui.Input;
            SetStyle(ControlStyles.AllPaintingInWmPaint | ControlStyles.OptimizedDoubleBuffer | ControlStyles.UserPaint | ControlStyles.ResizeRedraw, true);
        }
        protected override void OnPaint(PaintEventArgs e)
        {
            var g = e.Graphics;
            g.SmoothingMode = SmoothingMode.AntiAlias;
            var r = new Rectangle(0, 0, Width - 1, Height - 1);
            using (var path = Ui.Round(r, 5))
            {
                using (var sb = new SolidBrush(Ui.Input)) g.FillPath(sb, path);
                double v = Math.Max(0, Math.Min(100, Value));
                int w = (int)Math.Round(r.Width * v / 100.0);
                if (w > 0)
                {
                    if (w < 10) w = 10;
                    if (w > r.Width) w = r.Width;
                    g.SetClip(path);
                    using (var lg = new LinearGradientBrush(new Rectangle(0, 0, Math.Max(w, 1), Height), Fill1, Fill2, 0f))
                        g.FillRectangle(lg, 0, 0, w, Height);
                    g.ResetClip();
                }
                using (var p = new Pen(Ui.Line)) g.DrawPath(p, path);
            }
        }
    }

    class MainForm : Form
    {
        const double PAY_DAYS = 21.75;
        readonly CultureInfo zh = CultureInfo.GetCultureInfo("zh-CN");

        // 控件
        Label lblTitle, lblClock, lblDerive1, lblDerive2, lblTodayCap, lblToday, lblStatus,
              lblFishCap, lblFishMoney, lblFishSub, lblFishHint,
              lblMonthTitle, lblMonth, lblMonthStat, lblRates, lblFoot, lblQuote;
        TextBox txtMonthly, txtWork, txtL1, txtL2;
        Button btn0830, btn0900, btnFish;
        CheckBox chkTop;
        Bar barToday, barMonth;
        Timer timer;
        NotifyIcon tray;

        // 配置与状态
        double monthly = 10000;
        string startMode = "0900";
        double workHours = 8;
        int lunchS = 720, lunchE = 780;
        string fishDay = "";
        bool fishPaused = false;
        long pauseStartTicks = 0;
        double fishBanked = 0;

        // ---------- 比例布局引擎 ----------
        // ratios[c] = {fx, fy, fw, fh}：控件位置/尺寸相对父容器尺寸的比例（0~1）
        readonly Dictionary<Control, double[]> ratios = new Dictionary<Control, double[]>();
        readonly Dictionary<Control, float> basePt = new Dictionary<Control, float>();   // 设计字号
        readonly Dictionary<string, Font> fontCache = new Dictionary<string, Font>();
        double curScale = 0;      // 窗口相对设计画布的缩放系数（0=尚未应用）
        double dpiFactor = 1.0;   // 本机 DPI / 96（字号只随用户缩放，不随 DPI 双重放大）

        static readonly string[] Quotes = {
            "只要我摸鱼够快，KPI 就追不上我。",
            "钱是一秒一秒挣的，班是一天一天上的。",
            "牛马也需要正反馈：看着数字跳。",
            "带薪摸鱼，是牛马最后的倔强。",
            "草料按秒结算，牛马含泪进食。",
            "多拉一秒磨，多嚼一口草。",
            "打工不可怕，可怕的是不知道自己每小时值多少钱。"
        };

        public MainForm()
        {
            Text = "牛马草料计算器";
            BackColor = Color.FromArgb(11, 14, 20);
            Font = new Font("Microsoft YaHei UI", 9F);
            AutoScaleMode = AutoScaleMode.Dpi;
            AutoScaleDimensions = new SizeF(96F, 96F);
            ClientSize = new Size((int)Ui.DesignW, (int)Ui.DesignH);
            StartPosition = FormStartPosition.CenterScreen;
            FormBorderStyle = FormBorderStyle.Sizable;
            MaximizeBox = true;
            DoubleBuffered = true;

            BuildUi();
            LoadState();
            txtMonthly.Text = monthly > 0 ? monthly.ToString(zh) : "";
            txtWork.Text = workHours.ToString(zh);
            txtL1.Text = MinToHm(lunchS);
            txtL2.Text = MinToHm(lunchE);
            SyncModeButtons();
            SyncFishBtn();

            try { Icon = Icon.ExtractAssociatedIcon(Application.ExecutablePath); } catch { }
            InitTray();

            timer = new Timer { Interval = 100 };
            timer.Tick += delegate { RecalcUI(); };
            timer.Start();

            Resize += delegate
            {
                if (WindowState == FormWindowState.Minimized) Hide();
                else ApplyRatios();
            };
        }

        // ---------- UI 构建（BuildUi 里的坐标只是"设计画布 480x788"上的初始值，运行时全部转为比例） ----------
        static Label NewLabel(string text, int x, int y, Color fore, Font f)
        {
            return new Label
            {
                Text = text,
                Location = new Point(x, y),
                ForeColor = fore,
                Font = f,
                AutoSize = true
            };
        }
        static TextBox NewInput(int x, int y, int w, Font f)
        {
            return new TextBox
            {
                Location = new Point(x, y),
                Size = new Size(w, 28),
                BorderStyle = BorderStyle.FixedSingle,
                BackColor = Ui.Input,
                ForeColor = Ui.Text,
                Font = f
            };
        }

        // 水平居中（按父容器实测宽度，天然成比例）；纵向由比例引擎负责
        void Recenter(Label l, string text)
        {
            l.Text = text;
            if (l.Parent != null) l.Left = Math.Max(0, (l.Parent.ClientSize.Width - l.PreferredSize.Width) / 2);
        }

        // 定宽、按内容实测高度（自动换行的长文本）
        void FitWrap(Label l)
        {
            int w = Math.Max(l.Width, 10);
            var s = TextRenderer.MeasureText(l.Text, l.Font, new Size(w, int.MaxValue), TextFormatFlags.WordBreak);
            l.AutoSize = false;
            l.Height = Math.Max(s.Height + 2, l.Font.Height + 2);
        }

        void BuildUi()
        {
            var fLabel = new Font("Microsoft YaHei UI", 9F);
            var fSmall = new Font("Microsoft YaHei UI", 8.25F);
            var fNum = new Font("Consolas", 11F);

            lblTitle = NewLabel("牛马草料计算器", 20, 16, Ui.Text, new Font("Microsoft YaHei UI", 13F, FontStyle.Bold));
            chkTop = new CheckBox
            {
                Text = "置顶",
                Location = new Point(396, 20),
                Size = new Size(64, 22),
                ForeColor = Ui.Muted
            };
            chkTop.CheckedChanged += delegate { TopMost = chkTop.Checked; SaveState(); };
            lblClock = NewLabel("--", 20, 46, Ui.Text, new Font("Consolas", 11F, FontStyle.Bold));

            // 设置卡片
            var cSet = new Card { Location = new Point(20, 80), Size = new Size(440, 186) };
            cSet.Controls.Add(NewLabel("月总收入（元）", 16, 12, Ui.Muted, fLabel));
            txtMonthly = NewInput(16, 34, 148, fNum);
            txtMonthly.TextChanged += delegate
            {
                double v;
                if (double.TryParse(txtMonthly.Text.Trim(), NumberStyles.Float, zh, out v) && v > 0) { monthly = v; SaveState(); }
            };
            cSet.Controls.Add(txtMonthly);

            cSet.Controls.Add(NewLabel("上班时间", 184, 12, Ui.Muted, fLabel));
            btn0830 = ModeButton(184, 34, "08:30");
            btn0900 = ModeButton(258, 34, "09:00");
            btn0830.Click += delegate { startMode = "0830"; SyncModeButtons(); SaveState(); };
            btn0900.Click += delegate { startMode = "0900"; SyncModeButtons(); SaveState(); };
            cSet.Controls.Add(btn0830);
            cSet.Controls.Add(btn0900);

            cSet.Controls.Add(NewLabel("工时(h)", 348, 12, Ui.Muted, fLabel));
            txtWork = NewInput(348, 34, 76, fNum);
            txtWork.TextChanged += delegate
            {
                double v;
                if (double.TryParse(txtWork.Text.Trim(), NumberStyles.Float, zh, out v) && v >= 1 && v <= 16) { workHours = v; SaveState(); }
            };
            cSet.Controls.Add(txtWork);

            cSet.Controls.Add(NewLabel("午休（不计薪）", 16, 70, Ui.Muted, fLabel));
            txtL1 = NewInput(16, 92, 66, fNum);
            txtL2 = NewInput(102, 92, 66, fNum);
            txtL1.TextChanged += delegate { int v; if (TryParseHm(txtL1.Text, out v)) { lunchS = v; SaveState(); } };
            txtL2.TextChanged += delegate { int v; if (TryParseHm(txtL2.Text, out v)) { lunchE = v; SaveState(); } };
            cSet.Controls.Add(txtL1);
            cSet.Controls.Add(txtL2);
            cSet.Controls.Add(NewLabel("–", 86, 98, Ui.Muted, fLabel));

            lblDerive1 = NewLabel("", 16, 130, Ui.Soft, new Font("Microsoft YaHei UI", 9.5F, FontStyle.Bold));
            lblDerive2 = NewLabel("计薪口径：月收入 ÷ 21.75 天 ÷ 每日工时，午休不计薪", 16, 154, Ui.Muted, fSmall);
            cSet.Controls.Add(lblDerive1);
            cSet.Controls.Add(lblDerive2);

            // 今日卡片
            var cToday = new Card { Location = new Point(20, 276), Size = new Size(440, 152) };
            lblTodayCap = NewLabel("今日已累计", 0, 12, Ui.Muted, fLabel);
            cToday.Controls.Add(lblTodayCap);
            lblToday = NewLabel("¥0.0000", 0, 32, Ui.Green, new Font("Consolas", 27F, FontStyle.Bold));
            cToday.Controls.Add(lblToday);
            lblStatus = NewLabel("", 0, 96, Ui.Muted, new Font("Microsoft YaHei UI", 10F, FontStyle.Bold));
            cToday.Controls.Add(lblStatus);
            barToday = new Bar { Location = new Point(36, 128), Size = new Size(368, 12) };
            cToday.Controls.Add(barToday);

            // 摸鱼卡片
            var cFish = new Card { Location = new Point(20, 438), Size = new Size(440, 130) };
            btnFish = new Button
            {
                Text = "开始摸鱼",
                Location = new Point(16, 16),
                Size = new Size(128, 36),
                FlatStyle = FlatStyle.Flat,
                Font = new Font("Microsoft YaHei UI", 10F, FontStyle.Bold)
            };
            btnFish.FlatAppearance.BorderColor = Ui.Line;
            btnFish.FlatAppearance.BorderSize = 1;
            btnFish.Click += delegate
            {
                var k = DateTime.Now.ToString("yyyy-MM-dd");
                if (fishDay != k) { fishDay = k; fishPaused = false; pauseStartTicks = 0; fishBanked = 0; }
                if (fishPaused) { fishBanked += SessionPaidMin(); fishPaused = false; pauseStartTicks = 0; }
                else { fishPaused = true; pauseStartTicks = DateTime.Now.Ticks; }
                SaveState(); SyncFishBtn();
            };
            cFish.Controls.Add(btnFish);
            lblFishCap = NewLabel("今日摸鱼额度（带薪）", 164, 14, Ui.Muted, fSmall);
            lblFishMoney = NewLabel("¥0.00", 164, 30, Ui.Cyan, new Font("Consolas", 15F, FontStyle.Bold));
            lblFishSub = NewLabel("", 164, 66, Ui.Muted, fLabel);
            cFish.Controls.Add(lblFishCap);
            cFish.Controls.Add(lblFishMoney);
            cFish.Controls.Add(lblFishSub);
            lblFishHint = new Label
            {
                Text = "",
                Location = new Point(16, 84),
                Size = new Size(408, 38),
                ForeColor = Ui.Muted,
                Font = fSmall
            };
            cFish.Controls.Add(lblFishHint);

            // 本月卡片
            var cMonth = new Card { Location = new Point(20, 578), Size = new Size(440, 140) };
            lblMonthTitle = NewLabel("本月已累计", 0, 12, Ui.Muted, fLabel);
            cMonth.Controls.Add(lblMonthTitle);
            lblMonth = NewLabel("¥0.00", 0, 32, Ui.Gold, new Font("Consolas", 21F, FontStyle.Bold));
            cMonth.Controls.Add(lblMonth);
            barMonth = new Bar { Location = new Point(36, 88), Size = new Size(368, 12), Fill1 = Color.FromArgb(217, 119, 6), Fill2 = Color.FromArgb(253, 230, 138) };
            cMonth.Controls.Add(barMonth);
            lblMonthStat = NewLabel("", 0, 106, Ui.Muted, fLabel);
            cMonth.Controls.Add(lblMonthStat);

            lblRates = NewLabel("", 20, 726, Ui.Muted, new Font("Consolas", 9.5F));
            lblFoot = NewLabel("v1 口径：周一至周五计薪，暂未内置法定节假日调休表", 20, 750, Ui.Faint, fSmall);
            var rnd = new Random();
            lblQuote = NewLabel(Quotes[rnd.Next(Quotes.Length)], 20, 770, Ui.Muted, new Font("Microsoft YaHei UI", 9F, FontStyle.Italic));

            Controls.Add(lblTitle);
            Controls.Add(chkTop);
            Controls.Add(lblClock);
            Controls.Add(cSet);
            Controls.Add(cToday);
            Controls.Add(cFish);
            Controls.Add(cMonth);
            Controls.Add(lblRates);
            Controls.Add(lblFoot);
            Controls.Add(lblQuote);
        }

        Button ModeButton(int x, int y, string text)
        {
            var b = new Button
            {
                Text = text,
                Location = new Point(x, y),
                Size = new Size(70, 28),
                FlatStyle = FlatStyle.Flat,
                Font = new Font("Consolas", 10F, FontStyle.Bold)
            };
            b.FlatAppearance.BorderSize = 1;
            return b;
        }

        void SyncModeButtons()
        {
            SetModeLook(btn0830, startMode == "0830");
            SetModeLook(btn0900, startMode == "0900");
        }
        void SetModeLook(Button b, bool active)
        {
            if (active) { b.BackColor = Ui.Green; b.ForeColor = Color.FromArgb(6, 40, 28); }
            else { b.BackColor = Ui.Input; b.ForeColor = Ui.Soft; }
        }

        void SyncFishBtn()
        {
            btnFish.Text = fishPaused ? "继续搬砖" : "开始摸鱼";
            if (fishPaused) { btnFish.BackColor = Ui.Gold; btnFish.ForeColor = Color.FromArgb(58, 37, 3); }
            else { btnFish.BackColor = Ui.Input; btnFish.ForeColor = Ui.Text; }
        }

        // ---------- 托盘 ----------
        void InitTray()
        {
            try
            {
                tray = new NotifyIcon();
                tray.Icon = Icon;
                tray.Text = "牛马草料计算器";
                tray.Visible = true;
                var menu = new ContextMenuStrip();
                menu.Items.Add("打开", null, delegate { ShowUp(); });
                menu.Items.Add("退出", null, delegate { tray.Visible = false; Application.Exit(); });
                tray.ContextMenuStrip = menu;
                tray.DoubleClick += delegate { ShowUp(); };
            }
            catch { }
        }
        void ShowUp()
        {
            Show();
            WindowState = FormWindowState.Normal;
            Activate();
        }
        protected override void OnFormClosing(FormClosingEventArgs e)
        {
            if (tray != null) tray.Visible = false;
            base.OnFormClosing(e);
        }
        protected override void OnShown(EventArgs e)
        {
            base.OnShown(e);

            // 兜底：确保窗口装得下全部内容（个别设备窗体自动缩放不到位）
            int maxR = 0, maxB = 0;
            Walk(Controls, delegate(Control c)
            {
                if (c.Right > maxR) maxR = c.Right;
                if (c.Bottom > maxB) maxB = c.Bottom;
            });
            maxR += 20; maxB += 30;
            if (ClientSize.Width < maxR || ClientSize.Height < maxB)
                ClientSize = new Size(Math.Max(ClientSize.Width, maxR), Math.Max(ClientSize.Height, maxB));

            CaptureRatios();   // 把全部控件转为相对父容器的比例坐标
            ApplyRatios();     // 立即按当前窗口比例重排
            MinimumSize = new Size((int)(Width * 0.7), (int)(Height * 0.7));

            AuditLayout();     // 启动界面自检
        }

        // ---------- 比例布局引擎 ----------
        void Walk(System.Windows.Forms.Control.ControlCollection cs, Action<Control> a)
        {
            foreach (Control c in cs) { a(c); Walk(c.Controls, a); }
        }

        void CaptureRatios()
        {
            ratios.Clear(); basePt.Clear();
            Walk(Controls, delegate(Control c)
            {
                var p = c.Parent;
                if (p == null) return;
                double pw = p.ClientSize.Width, ph = p.ClientSize.Height;
                if (pw < 1 || ph < 1) return;
                ratios[c] = new double[] { c.Left / pw, c.Top / ph, c.Width / pw, c.Height / ph };
                basePt[c] = c.Font.Size;
            });
            try { dpiFactor = DeviceDpi / 96.0; } catch { dpiFactor = 1.0; }
            if (dpiFactor < 0.5) dpiFactor = 1.0;
            curScale = 0;
        }

        // 窗口尺寸变化 → 全部控件按比例重排（含字体等比、内容块居中）
        void ApplyRatios()
        {
            if (ratios.Count == 0) return;
            double s = Math.Min(ClientSize.Width / Ui.DesignW, ClientSize.Height / Ui.DesignH);
            if (s > 4) s = 4;
            if (s < 0.35) s = 0.35;
            if (curScale > 0 && Math.Abs(s - curScale) < 0.002) return;
            curScale = s;
            double fontMult = s / dpiFactor;   // 字号只跟随用户缩放，不随 DPI 双重放大
            int ox = Math.Max(0, (ClientSize.Width - (int)Math.Round(Ui.DesignW * s)) / 2);
            int oy = Math.Max(0, (ClientSize.Height - (int)Math.Round(Ui.DesignH * s)) / 2);

            // 先排窗体直属控件（含卡片），再排卡片内部控件（依赖父容器新尺寸）
            for (int pass = 0; pass < 2; pass++)
            {
                bool nested = pass == 1;
                foreach (var kv in ratios)
                {
                    var c = kv.Key;
                    bool onForm = c.Parent == this;
                    if (onForm == nested) continue;
                    double[] r = kv.Value;
                    int pw, ph;
                    if (onForm) { pw = ClientSize.Width; ph = ClientSize.Height; }
                    else { pw = c.Parent.ClientSize.Width; ph = c.Parent.ClientSize.Height; }
                    int x = ox + (int)Math.Round(r[0] * pw);
                    int y = oy + (int)Math.Round(r[1] * ph);
                    int w = (int)Math.Round(r[2] * pw);
                    int h = (int)Math.Round(r[3] * ph);
                    c.Bounds = new Rectangle(x, y, w, h);
                    float pt;
                    if (basePt.TryGetValue(c, out pt)) c.Font = ScaledFont(c, pt, fontMult);
                }
            }
        }

        Font ScaledFont(Control c, float designPt, double mult)
        {
            float size = (float)Math.Round(designPt * mult * 2f) / 2f;
            if (size < 5f) size = 5f;
            string key = c.Font.FontFamily.Name + "|" + size.ToString(zh) + "|" + (int)c.Font.Style;
            Font f;
            if (!fontCache.TryGetValue(key, out f))
            {
                f = new Font(c.Font.FontFamily, size, c.Font.Style);
                fontCache[key] = f;
            }
            return f;
        }

        // 拉伸窗口时锁定纵横比：拖任意边/角，另一维自动等比联动
        protected override void WndProc(ref Message m)
        {
            if (m.Msg == 0x214 && ratios.Count > 0) // WM_SIZING
            {
                var r = (RECT)Marshal.PtrToStructure(m.LParam, typeof(RECT));
                int w = r.Right - r.Left;
                int h = r.Bottom - r.Top;
                int edge = (int)m.WParam;             // 1左 2右 3上 4左上 5右上 6下 7左下 8右下
                bool dragTop = edge == 3 || edge == 4 || edge == 5;
                bool dragLeft = edge == 1 || edge == 4 || edge == 7;
                if (edge == 1 || edge == 2 || edge >= 4)
                {
                    int nh = (int)Math.Round(w / (Ui.DesignW / Ui.DesignH));
                    if (dragTop) r.Top = r.Bottom - nh; else r.Bottom = r.Top + nh;
                }
                else
                {
                    int nw = (int)Math.Round(h * (Ui.DesignW / Ui.DesignH));
                    if (dragLeft) r.Left = r.Right - nw; else r.Right = r.Left + nw;
                }
                Marshal.StructureToPtr(r, m.LParam, true);
                m.Result = (IntPtr)1;
                return;
            }
            base.WndProc(ref m);
        }

        [StructLayout(LayoutKind.Sequential)]
        private struct RECT { public int Left, Top, Right, Bottom; }

        // ---------- 界面自检 ----------
        void AuditLayout()
        {
            var lines = new List<string>();
            try { lines.Add("DPI=" + DeviceDpi + "  缩放系数=" + curScale.ToString("0.00", zh) + "  自检时间=" + DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss")); }
            catch { lines.Add("自检时间=" + DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss")); }
            lines.Add(NetFrameworkInfo());
            int overflows = 0;
            Audit(Controls, lines, ref overflows);

            try
            {
                var f = Path.Combine(Path.GetDirectoryName(StateFile()), "layout_audit.txt");
                Directory.CreateDirectory(Path.GetDirectoryName(f));
                File.WriteAllLines(f, lines);
            }
            catch { }

            if (tray != null)
            {
                var msg = overflows == 0
                    ? "当前缩放下所有文字完整显示，无溢出 ✓"
                    : "发现 " + overflows + " 处文字溢出，详见 layout_audit.txt";
                try { tray.ShowBalloonTip(2000, "牛马草料计算器 · 界面自检", msg, ToolTipIcon.Info); } catch { }
            }
        }
        void Audit(System.Windows.Forms.Control.ControlCollection cs, List<string> lines, ref int overflows)
        {
            foreach (Control c in cs)
            {
                if (c is Label && c.Parent != null)
                {
                    if (c.Right > c.Parent.ClientSize.Width + 1 || c.Bottom > c.Parent.ClientSize.Height + 1)
                    {
                        overflows++;
                        lines.Add("[溢出] \"" + c.Text + "\" Right=" + c.Right + " Bottom=" + c.Bottom
                            + " 父容器=" + c.Parent.ClientSize.Width + "x" + c.Parent.ClientSize.Height);
                    }
                }
                Audit(c.Controls, lines, ref overflows);
            }
        }

        // 读取本机 .NET Framework 精确版本（Release ≥ 528040 即 4.8）
        string NetFrameworkInfo()
        {
            try
            {
                using (var k = Microsoft.Win32.RegistryKey.OpenBaseKey(Microsoft.Win32.RegistryHive.LocalMachine, Microsoft.Win32.RegistryView.Registry32)
                    .OpenSubKey(@"SOFTWARE\Microsoft\NET Framework Setup\NDP\v4\Full"))
                {
                    if (k != null)
                    {
                        object rel = k.GetValue("Release"), ver = k.GetValue("Version");
                        if (rel != null)
                        {
                            int r = Convert.ToInt32(rel);
                            string name = r >= 533320 ? "4.8.1" : r >= 528040 ? "4.8" : r >= 461808 ? "4.7.2" : "更旧(需升级)";
                            return "NET=." + name + " (Release=" + r + ")";
                        }
                        if (ver != null) return "NET Version=" + ver;
                    }
                }
            }
            catch { }
            return "NET=未检测到 .NET Framework 4.x（无法运行）";
        }

        // ---------- 计算模型 ----------
        bool IsWorkday(DateTime d) { var w = (int)d.DayOfWeek; return w >= 1 && w <= 5; }

        int SStart() { return startMode == "0830" ? 510 : 540; }

        void Schedule(out int s, out int ls, out int le, out int workMin, out int morning, out int endT)
        {
            s = SStart(); ls = lunchS; le = lunchE;
            if (le <= ls) le = ls + 60;
            workMin = Math.Max(1, (int)Math.Round(workHours * 60));
            morning = Math.Max(0, ls - s);
            endT = le + Math.Max(0, workMin - morning);
        }

        double PaidMin(double t, int s, int ls, int le, int workMin, int morning, int endT)
        {
            if (t <= s) return 0;
            if (t < ls) return t - s;
            if (t < le) return morning;              // 午休冻结
            if (t >= endT) return workMin;           // 下班封顶
            return morning + (t - le);
        }

        double NowMin(DateTime d)
        {
            return d.Hour * 60 + d.Minute + d.Second / 60.0 + d.Millisecond / 60000.0;
        }

        double SessionPaidMin()
        {
            if (!fishPaused) return 0;
            var now = DateTime.Now;
            if (!IsWorkday(now)) return 0;
            var ps = new DateTime(pauseStartTicks);
            double startMin = ps.Date == now.Date ? NowMin(ps) : 0;
            int s, ls, le, wm, mo, en;
            Schedule(out s, out ls, out le, out wm, out mo, out en);
            return Math.Max(0, PaidMin(NowMin(now), s, ls, le, wm, mo, en) - PaidMin(startMin, s, ls, le, wm, mo, en));
        }

        // ---------- 渲染 ----------
        void RecalcUI()
        {
            var now = DateTime.Now;
            var k = now.ToString("yyyy-MM-dd");
            if (fishDay != k) { fishDay = k; fishPaused = false; pauseStartTicks = 0; fishBanked = 0; SaveState(); }

            lblClock.Text = "本地时间  " + now.ToString("yyyy-MM-dd") + " 星期" + "日一二三四五六"[(int)now.DayOfWeek] + " " + now.ToString("HH:mm:ss");
            double M = monthly > 0 ? monthly : 0;
            int s, ls, le, wm, mo, en;
            Schedule(out s, out ls, out le, out wm, out mo, out en);
            double daily = M / PAY_DAYS;
            double perMin = wm > 0 ? daily / wm : 0;
            double perSec = wm > 0 ? daily / (wm * 60) : 0;
            lblDerive1.Text = "下班 " + P2(en / 60 % 24) + ":" + P2(en % 60)
                + " ｜ 日均 ¥" + Money(daily, 2)
                + " ｜ 时薪 ¥" + Money(wm > 0 ? daily / (wm / 60.0) : 0, 2);

            double t = NowMin(now);
            bool weekend = !IsWorkday(now);
            bool inLunch = t >= ls && t < le;
            bool inPaid = !weekend && t > s && t < en && !inLunch;
            double paid = weekend ? 0 : PaidMin(t, s, ls, le, wm, mo, en);
            double todayPaid = paid * perMin;

            int days = 0;
            for (var d = new DateTime(now.Year, now.Month, 1); d.Date < now.Date; d = d.AddDays(1))
                if (IsWorkday(d)) days++;
            double monthPaid = days * daily + todayPaid;

            barToday.Value = weekend ? 0 : Math.Min(100, paid / wm * 100);
            double mp = M > 0 ? Math.Min(100, monthPaid / M * 100) : 0;
            barMonth.Value = mp;

            // 摸鱼额度
            double ftm = fishBanked + SessionPaidMin();
            double fishMoney = ftm * perMin;
            lblFishMoney.Text = "¥" + Money(fishMoney, 2);
            lblFishSub.Text = "已摸 " + ftm.ToString("0.0", zh) + " 分钟 ｜ 占今日 "
                + (todayPaid > 0 ? Math.Min(100, fishMoney / todayPaid * 100) : 0).ToString("0.0", zh) + "%";
            bool offDuty = weekend || t <= s || t >= en || inLunch;
            lblFishHint.Text = "摸鱼期间工资照涨，涨的钱单独计入摸鱼额度；午休、下班、周末不计额。"
                + (fishPaused && offDuty ? " ⚠ 当前不在计薪时段，不计额" : "");
            FitWrap(lblFishHint);

            // 状态与倒计时（文字按场景着色）
            string b, c;
            Color col = Ui.Muted;
            if (weekend) { b = "周末休息中"; c = "今天不计薪，快乐无价"; }
            else if (t < s) { b = "尚未上班"; c = "距开工 " + Dur(s - t); }
            else if (t < ls) { b = "上午搬砖中"; c = "距午休 " + Dur(ls - t); col = Ui.Green; }
            else if (t < le) { b = "午休中（不带薪）"; c = "距下午开工 " + Dur(le - t); col = Ui.Gold; }
            else if (t < en) { b = "下午搬砖中"; c = "距下班 " + Dur(en - t); col = Ui.Green; }
            else { b = "今日已收工"; c = "日结 ¥" + Money(daily, 2) + " 已入账"; col = Ui.Green; }
            if (fishPaused && inPaid) { b = "摸鱼中（带薪）"; col = Ui.Cyan; }

            // 居中文本（实测宽度水平居中；纵向由比例引擎负责）
            Recenter(lblTodayCap, lblTodayCap.Text);
            Recenter(lblToday, "¥" + Money(todayPaid, 4));
            Recenter(lblStatus, b + " · " + c);
            lblStatus.ForeColor = col;
            Recenter(lblMonthTitle, "本月（" + now.Month + "月）已累计");
            Recenter(lblMonth, "¥" + Money(monthPaid, 2));
            Recenter(lblMonthStat, "本月进度 " + mp.ToString("0.0", zh) + "% ｜ 已计 " + days + " 个工作日（含今日实时）");
            Recenter(lblRates, "每分钟 ¥" + Money(perMin, 4) + " ｜ 每秒 ¥" + perSec.ToString("0.000000", zh) + " ｜ 月计薪 21.75 天");
            Recenter(lblFoot, lblFoot.Text);
            Recenter(lblQuote, lblQuote.Text);

            Text = "今日¥" + Money(todayPaid, 2) + " · 本月¥" + Money(monthPaid, 2) + " ｜ 牛马草料计算器";
        }

        // ---------- 工具 ----------
        string Money(double v, int frac) { return v.ToString("N" + frac, zh); }
        static string P2(long n) { return n < 10 ? "0" + n : n.ToString(); }
        static string P2(int n) { return n < 10 ? "0" + n : n.ToString(); }
        string Dur(double mins)
        {
            long s = (long)Math.Round(Math.Max(0, mins) * 60);
            return P2(s / 3600) + ":" + P2(s % 3600 / 60) + ":" + P2(s % 60);
        }
        string MinToHm(int v) { return P2(v / 60) + ":" + P2(v % 60); }
        static bool TryParseHm(string text, out int minutes)
        {
            minutes = -1;
            if (string.IsNullOrEmpty(text)) return false;
            var p = text.Trim().Split(':');
            int h, m;
            if (p.Length != 2 || !int.TryParse(p[0], out h) || !int.TryParse(p[1], out m)) return false;
            if (h < 0 || h > 23 || m < 0 || m > 59) return false;
            minutes = h * 60 + m;
            return true;
        }

        // ---------- 持久化 ----------
        string StateFile()
        {
            return Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "NiuMaIncome", "state.ini");
        }
        void LoadState()
        {
            try
            {
                var f = StateFile();
                if (!File.Exists(f)) return;
                foreach (var line in File.ReadAllLines(f))
                {
                    int i = line.IndexOf('=');
                    if (i < 1) continue;
                    var key = line.Substring(0, i);
                    var val = line.Substring(i + 1);
                    double dv; int iv; long lv; bool bv;
                    switch (key)
                    {
                        case "monthly": if (double.TryParse(val, NumberStyles.Float, zh, out dv) && dv > 0) monthly = dv; break;
                        case "startMode": if (val == "0830" || val == "0900") startMode = val; break;
                        case "workHours": if (double.TryParse(val, NumberStyles.Float, zh, out dv) && dv >= 1 && dv <= 16) workHours = dv; break;
                        case "lunchS": if (int.TryParse(val, out iv) && iv >= 0 && iv < 1440) lunchS = iv; break;
                        case "lunchE": if (int.TryParse(val, out iv) && iv >= 0 && iv < 1440) lunchE = iv; break;
                        case "fishDay": fishDay = val; break;
                        case "banked": if (double.TryParse(val, NumberStyles.Float, zh, out dv) && dv >= 0) fishBanked = dv; break;
                        case "paused": if (bool.TryParse(val, out bv)) fishPaused = bv; break;
                        case "pauseStart": if (long.TryParse(val, out lv)) pauseStartTicks = lv; break;
                        case "topmost": if (bool.TryParse(val, out bv)) chkTop.Checked = bv; break;
                    }
                }
            }
            catch { }
        }
        void SaveState()
        {
            try
            {
                var f = StateFile();
                Directory.CreateDirectory(Path.GetDirectoryName(f));
                File.WriteAllLines(f, new[]
                {
                    "monthly=" + monthly.ToString(zh),
                    "startMode=" + startMode,
                    "workHours=" + workHours.ToString(zh),
                    "lunchS=" + lunchS,
                    "lunchE=" + lunchE,
                    "fishDay=" + fishDay,
                    "banked=" + fishBanked.ToString(zh),
                    "paused=" + fishPaused,
                    "pauseStart=" + pauseStartTicks,
                    "topmost=" + chkTop.Checked
                });
            }
            catch { }
        }
    }
}
