package com.chinasofti.huateng.common.utils.captcha;

import java.util.Random;


public class SvgCaptcha
{
    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
    private static final Random RANDOM = new Random();

    private final int width;
    private final int height;
    private final int length;
    private final String code;

    public SvgCaptcha(int width, int height, int length)
    {
        this.width = width;
        this.height = height;
        this.length = length;
        this.code = generateCode();
    }

    private String generateCode()
    {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++)
        {
            sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }
        return sb.toString();
    }

    public String text()
    {
        return code;
    }

    public String toBase64Svg()
    {
        StringBuilder svg = new StringBuilder();
        svg.append("<svg xmlns='http://www.w3.org/2000/svg' width='")
           .append(width)
           .append("' height='")
           .append(height)
           .append("'>");

        svg.append("<rect width='100%' height='100%' fill='#f7f9fa'/>");

        for (int i = 0; i < 4; i++)
        {
            String color = String.format("#%06x", RANDOM.nextInt(0xFFFFFF));
            svg.append(String.format(
                    "<line x1='%d' y1='%d' x2='%d' y2='%d' stroke='%s' stroke-width='1'/>",
                    RANDOM.nextInt(width), RANDOM.nextInt(height),
                    RANDOM.nextInt(width), RANDOM.nextInt(height), color));
        }

        for (int i = 0; i < code.length(); i++)
        {
            char c = code.charAt(i);
            int x = 12 + i * 24;
            int y = 30 + RANDOM.nextInt(10) - 5;
            int rotate = RANDOM.nextInt(30) - 15;
            String color = String.format("#%06x", RANDOM.nextInt(0x333333) + 0x666666);
            svg.append(String.format(
                    "<text x='%d' y='%d' fill='%s' font-size='26' font-family='monospace' transform='rotate(%d %d %d)'>%c</text>",
                    x, y, color, rotate, x, y, c));
        }

        svg.append("</svg>");

        String svgXml = svg.toString();
        return java.util.Base64.getEncoder().encodeToString(svgXml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
