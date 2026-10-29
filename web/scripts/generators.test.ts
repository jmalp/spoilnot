import { describe, expect, it } from 'vitest';
import { innerSvg, launcherSvg, names } from './gen-icons.ts';
import { parse, unescape } from './gen-strings.ts';
import { parseSchemes, parseStatus, parseThemes } from './gen-theme.ts';

describe('gen-strings', () => {
  it('reads strings and plurals and undoes Android escapes', () => {
    const xml = `<resources>
      <!-- <string name="commented">no</string> -->
      <string name="share_message">Join \\"%1$s\\" &amp; don\\'t wait</string>
      <plurals name="plan_servings">
        <item quantity="one">%d serving</item>
        <item quantity="other">%d servings</item>
      </plurals>
    </resources>`;
    expect(parse(xml)).toEqual({
      strings: { share_message: 'Join "%1$s" & don\'t wait' },
      plurals: { plan_servings: { one: '%d serving', other: '%d servings' } },
    });
    expect(unescape('Line\\none &lt;b&gt;')).toBe('Line\none <b>');
  });
});

describe('gen-theme', () => {
  it('reads color schemes, the themes in order and the status colors', () => {
    const colors = `internal val LightColors =
    lightColorScheme(
        primary = Color(0xFF226A4C),
        onSurfaceVariant = Color(0xFF404943),
    )`;
    const schemes = parseSchemes(colors);
    expect(schemes).toEqual({ LightColors: { primary: '#226a4c', onSurfaceVariant: '#404943' } });
    const theme = `enum class ColorTheme(val id: String) {
    Wallpaper("wallpaper"),
    SpoilNot("spoilnot"),
    ;
    fun scheme(dark: Boolean) = when (this) {
        Wallpaper -> null
        SpoilNot -> if (dark) DarkColors else LightColors
    }`;
    expect(parseThemes(theme)).toEqual([{ id: 'spoilnot', light: 'LightColors', dark: 'DarkColors' }]);
    const status = `internal val LightStatusColors =
    StatusColors(
        fresh = Color(0xFF2E7D32),
        noDate = LightColors.onSurfaceVariant,
    )
internal val DarkStatusColors =
    StatusColors(
        fresh = Color(0xFF81C784),
        noDate = LightColors.onSurfaceVariant,
    )`;
    expect(parseStatus(status, schemes).light).toEqual({ fresh: '#2e7d32', noDate: '#404943' });
  });
});

describe('gen-icons', () => {
  it('takes the inner markup of 24 by 24 icons and checks the list', () => {
    expect(innerSvg('<svg xmlns="x" viewBox="0 0 24 24"><path d="M1 1z"/></svg>')).toBe('<path d="M1 1z"/>');
    expect(names('# tabs\nround/home\nround/home\noutlined/home\n')).toEqual(['outlined/home', 'round/home']);
    expect(() => names('filled/home')).toThrow();
  });

  it('turns the adaptive launcher icon into SVG, cropped to what a launcher shows', () => {
    const foreground =
      '<vector><path android:fillColor="@color/ic_launcher_foreground" android:fillType="evenOdd" android:pathData="M1,1Z" /></vector>';
    const colors =
      '<color name="ic_launcher_background">#FF2E7D5B</color><color name="ic_launcher_foreground">#FFF5FBF5</color>';
    const svg = launcherSvg(foreground, colors, true);
    expect(svg).toContain('viewBox="18 18 72 72"');
    expect(svg).toContain('<rect width="108" height="108" fill="#2e7d5b"/>');
    expect(svg).toContain('<path fill="#f5fbf5" fill-rule="evenodd" d="M1,1Z"/>');
  });
});
