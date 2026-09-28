// USMIND Self-Extractor stub
// 用法: 将本程序与 usmind-setup.zip 拼接为单个 exe，运行时自动解压到 %LOCALAPPDATA%\USMIND 并启动 USMIND.exe
// 支持参数 --extract-only: 只解压不启动（用于验证）
using System;
using System.IO;
using System.IO.Compression;
using System.Diagnostics;
using System.Windows.Forms;

class SelfExtract {
    static int Main(string[] args) {
        try {
            bool extractOnly = false;
            foreach (string a in args) if (a == "--extract-only") extractOnly = true;

            string exe = Process.GetCurrentProcess().MainModule.FileName;
            long zipStart = LocateZipStart(exe);
            string target = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "USMIND");

            bool needExtract = !File.Exists(Path.Combine(target, "USMIND.exe"));
            if (needExtract) {
                Directory.CreateDirectory(target);
                using (FileStream fs = new FileStream(exe, FileMode.Open, FileAccess.Read))
                using (ZipArchive zip = new ZipArchive(new SubStream(fs, zipStart), ZipArchiveMode.Read)) {
                    zip.ExtractToDirectory(target);
                }
            }

            if (extractOnly) return 0;

            if (!File.Exists(Path.Combine(target, "USMIND.exe"))) {
                MessageBox.Show("解压失败，未找到 USMIND.exe", "USMIND",
                    MessageBoxButtons.OK, MessageBoxIcon.Error);
                return 1;
            }

            Process.Start(new ProcessStartInfo(Path.Combine(target, "USMIND.exe")) {
                WorkingDirectory = target
            });
            return 0;
        } catch (Exception e) {
            MessageBox.Show("启动失败: " + e.Message, "USMIND",
                MessageBoxButtons.OK, MessageBoxIcon.Error);
            return 1;
        }
    }

    // 从 exe 尾部扫描 EOCD(PK\x05\x06)，计算内嵌 zip 的起始偏移
    static long LocateZipStart(string exe) {
        using (FileStream fs = new FileStream(exe, FileMode.Open, FileAccess.Read)) {
            long len = fs.Length;
            int tail = (int)Math.Min(len, 65536 + 22);
            byte[] buf = new byte[tail];
            fs.Position = len - tail;
            fs.Read(buf, 0, tail);
            for (int i = tail - 22; i >= 0; i--) {
                if (buf[i] == 0x50 && buf[i + 1] == 0x4B &&
                    buf[i + 2] == 0x05 && buf[i + 3] == 0x06) {
                    long eocdPos = len - tail + i;
                    long cdSize = BitConverter.ToUInt32(buf, i + 12);
                    long cdOffset = BitConverter.ToUInt32(buf, i + 16);
                    return eocdPos - cdSize - cdOffset;
                }
            }
        }
        throw new InvalidDataException("未找到内嵌压缩包 (EOCD)");
    }
}

// 只读子流：从指定偏移开始的原始文件流视图
class SubStream : Stream {
    Stream _base;
    long _start;
    public SubStream(Stream baseStream, long start) {
        _base = baseStream;
        _start = start;
        _base.Position = start;
    }
    public override bool CanRead { get { return true; } }
    public override bool CanSeek { get { return true; } }
    public override bool CanWrite { get { return false; } }
    public override long Length { get { return _base.Length - _start; } }
    public override long Position {
        get { return _base.Position - _start; }
        set { _base.Position = _start + value; }
    }
    public override int Read(byte[] buffer, int offset, int count) {
        long remain = Length - Position;
        if (remain <= 0) return 0;
        if (count > remain) count = (int)remain;
        return _base.Read(buffer, offset, count);
    }
    public override long Seek(long offset, SeekOrigin origin) {
        long pos = origin == SeekOrigin.Begin ? offset :
                   origin == SeekOrigin.Current ? Position + offset : Length + offset;
        Position = pos;
        return pos;
    }
    public override void Flush() { }
    public override void SetLength(long value) { throw new NotSupportedException(); }
    public override void Write(byte[] buffer, int offset, int count) { throw new NotSupportedException(); }
}
