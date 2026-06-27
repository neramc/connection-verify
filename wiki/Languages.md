# Languages

All console and command messages are localizable. Connection Verify ships with
**English (`en`)** and **Korean (`ko`)**.

## Switching language

1. Set `language` in `config.yml` to a language code (e.g. `ko`).
2. Make sure `plugins/connection-verify/lang/<code>.yml` exists.
3. Run `/connectionverify reload`.

If a key is missing from your language file, the bundled English value is used
as a fallback, so messages always render.

## Adding a translation

1. Copy `lang/en.yml` to `lang/<code>.yml` (e.g. `lang/de.yml`).
2. Translate the values — **keep the keys and `<placeholders>` unchanged**.
3. Set `language: <code>` and reload.

### Formatting

Messages use the [MiniMessage](https://docs.advntr.dev/minimessage/format.html)
format, so you can colour and style them with tags like `<green>`, `<bold>`,
`<gradient>`, etc.

Placeholders are written in angle brackets and filled in by the plugin, e.g.:

```yaml
console:
  connection-number: "<aqua>Connection number </aqua><yellow><bold><number></bold></yellow>"
```

Use `[square brackets]` for literal hint text so it is not mistaken for a
placeholder.

> Set `console.use-colors: false` to strip all styling (useful for plain-text
> log scraping).

## Contributing a language

Translations are welcome! Open a pull request adding your `lang/<code>.yml`, and
it can be bundled for everyone.
