package org.abimon.mockana

import io.ktor.http.HttpMethod
import org.parboiled.Action
import org.parboiled.BaseParser
import org.parboiled.Parboiled
import org.parboiled.Rule
import org.parboiled.annotations.BuildParseTree
import org.parboiled.support.StringVar
import org.parboiled.support.Var
import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption

@BuildParseTree
open class MockanaParser constructor(constructed: Boolean) : BaseParser<Any>() {
    companion object {
        operator fun invoke(): MockanaParser = Parboiled.createParser(MockanaParser::class.java, true)
    }

    open val digitsLower = charArrayOf(
        '0', '1', '2', '3', '4', '5',
        '6', '7', '8', '9', 'a', 'b',
        'c', 'd', 'e', 'f', 'g', 'h',
        'i', 'j', 'k', 'l', 'm', 'n',
        'o', 'p', 'q', 'r', 's', 't',
        'u', 'v', 'w', 'x', 'y', 'z'
    )

    open val digitsUpper = charArrayOf(
        '0', '1', '2', '3', '4', '5',
        '6', '7', '8', '9', 'A', 'B',
        'C', 'D', 'E', 'F', 'G', 'H',
        'I', 'J', 'K', 'L', 'M', 'N',
        'O', 'P', 'Q', 'R', 'S', 'T',
        'U', 'V', 'W', 'X', 'Y', 'Z'
    )

    open val whitespace =
        (Character.MIN_VALUE until Character.MAX_VALUE).filter { Character.isWhitespace(it) }.toCharArray()

    open fun Digit(): Rule = Digit(10)
    open fun Digit(base: Int): Rule =
        FirstOf(AnyOf(digitsLower.sliceArray(0 until base)), AnyOf(digitsUpper.sliceArray(0 until base)))

    open fun WhitespaceCharacter(): Rule = AnyOf(whitespace)
    open fun Whitespace(): Rule = OneOrMore(WhitespaceCharacter())
    open fun OptionalWhitespace(): Rule = ZeroOrMore(WhitespaceCharacter())
    open fun InlineWhitespaceCharacter(): Rule = AnyOf(charArrayOf('\t', ' '))
    open fun InlineWhitespace(): Rule = OneOrMore(InlineWhitespaceCharacter())
    open fun OptionalInlineWhitespace(): Rule = ZeroOrMore(InlineWhitespaceCharacter())

    open fun Mockana(): Rule = OneOrMany(Route())

    open fun OneOrMany(rule: Rule): Rule = OneOrMany(rule, Whitespace())
    open fun OneOrMany(rule: Rule, delimiter: Rule): Rule =
        Sequence(OptionalInlineWhitespace(), rule, ZeroOrMore(delimiter, OptionalInlineWhitespace(), rule))

    open fun Route(): Rule {
        val methodVar = Var<String>()
        val pathVar = Var<String>()

        return Sequence(
            FirstOf(HttpMethod.DefaultMethods.map(HttpMethod::value).toTypedArray()),
            Action<Any> { context -> methodVar.set(context.match) },
            OptionalInlineWhitespace(),
            Call(Sequence(String(), Action<Any> { pathVar.set(pop() as String) })),
            OptionalInlineWhitespace(),
            '{',
            OptionalInlineWhitespace(),
            '\n',
            RouteResponse(),
            '\n',
            OptionalInlineWhitespace(),
            '}',
            Action<Any> {
                push(
                    MockRoute(
                        HttpMethod.parse(methodVar.get().toUpperCase()),
                        pathVar.get().toRegex(),
                        pop() as MockResponse
                    )
                )
            }
        )
    }

    open fun RouteResponse(): Rule {
        val responseVar = Var<MockResponse>()
        val rule = FirstOf(
            Sequence(
                "body",
                OptionalInlineWhitespace(),
                '=',
                OptionalInlineWhitespace(),
                FirstOf(
                    Sequence(
                        StringParameter(),
                        Action<Any> {
                            responseVar.get().body =
                                MockanaOutgoingContent.MockanaTextOutgoingContent(pop() as String); true
                        },
                        ';'
                    ),
                    Sequence(
                        Parameter("file", String()),
                        Action<Any> {
                            responseVar.get().body =
                                MockanaOutgoingContent.MockanaFileOutgoingContent(File(pop() as String)); true
                        }
                    ),
                    Sequence(
                        FileHashParameter(),
                        Action<Any> {
                            val algorithm = pop() as String
                            val fileName = pop() as String

                            responseVar.get().body = MockanaOutgoingContent.MockanaTextOutgoingContent(
                                FileChannel.open(
                                    File(fileName).toPath(),
                                    StandardOpenOption.READ
                                ).hash(algorithm)
                            )
                            return@Action true
                        },
                        ';'
                    )
                )
            ),
            Sequence(
                "status",
                Optional(
                    Optional('_'),
                    "code"
                ),
                OptionalInlineWhitespace(),
                '=',
                OptionalInlineWhitespace(),
                OneOrMore(Digit()),
                Action<Any> {
                    responseVar.get().statusCode =
                        match().toInt(); true
                },
                ';'
            ),
            Sequence(
                Parameter(
                    "header",
                    Sequence(
                        Optional(
                            FirstOf(
                                "key",
                                "name"
                            ),
                            OptionalInlineWhitespace(),
                            FirstOf(
                                '=',
                                ':'
                            ),
                            OptionalInlineWhitespace()
                        ),
                        String(),
                        OptionalInlineWhitespace(),
                        ',',
                        OptionalInlineWhitespace(),
                        Optional(
                            "value",
                            OptionalInlineWhitespace(),
                            FirstOf(
                                '=',
                                ':'
                            ),
                            OptionalInlineWhitespace()
                        ),
                        String()
                    )
                ),
                Action<Any> {
                    val value = pop() as String
                    val key = pop() as String

                    responseVar.get().headers.add(key to value)
                    return@Action true
                },
                ';'
            )
        )

        return Sequence(
            Action<Any> { responseVar.set(MockResponse()) },
            OneOrMany(rule),
            Action<Any> { push(responseVar.get()) }
        )
    }

    open fun StringParameter(): Rule = Parameter("str", String())
    open fun FileHashParameter(): Rule = Parameter(
        "file_hash",
        Sequence(
            Optional(
                FirstOf(
                    Sequence("file", Optional(Optional('_'), FirstOf("name", "path"))),
                    "name",
                    "path"
                ),
                OptionalInlineWhitespace(),
                FirstOf(
                    '=',
                    ':'
                ),
                OptionalInlineWhitespace()
            ),
            String(),
            OptionalInlineWhitespace(),
            ',',
            OptionalInlineWhitespace(),
            Optional(
                FirstOf(
                    "hash",
                    Sequence("alg", Optional("orithm"))
                ),
                OptionalInlineWhitespace(),
                FirstOf(
                    '=',
                    ':'
                ),
                OptionalInlineWhitespace()
            ),
            String()
        )
    )

    open fun Parameter(name: String, parameterRule: Rule): Rule =
        Sequence(
            name,
            OptionalInlineWhitespace(),
            Call(parameterRule),
            OptionalInlineWhitespace()
        )

    open fun Call(rule: Rule): Rule =
        Sequence(
            '(',
            OptionalInlineWhitespace(),
            rule,
            OptionalInlineWhitespace(),
            ')'
        )

    open fun String(): Rule {
        val str = Var<String>()

        return Sequence(
            "\"",
            Action<Any> { str.set("") },
            OneOrMore(
                FirstOf(
                    Sequence(
                        "\\",
                        FirstOf(
                            Sequence(
                                FirstOf(
                                    "\"",
                                    "\\",
                                    "/",
                                    "b",
                                    "f",
                                    "n",
                                    "r",
                                    "t",
                                    "$"
                                ),
                                Action<Any> {
                                    when (match()) {
                                        "\"" -> str.set(str.get() + "\"")
                                        "\\" -> str.set(str.get() + "\\")
                                        "/" -> str.set(str.get() + "/")
                                        "b" -> str.set(str.get() + "\b")
                                        "f" -> str.set(str.get() + 0xC.toChar())
                                        "n" -> str.set(str.get() + "\n")
                                        "r" -> str.set(str.get() + "\r")
                                        "t" -> str.set(str.get() + "\t")
                                        "$" -> str.set(str.get() + "$")
                                    }

                                    return@Action true
                                }
                            ),
                            Sequence(
                                "u",
                                NTimes(4, Digit(16)),
                                Action<Any> { str.set(str.get() + match().toInt(16).toChar()) }
                            )
                        )
                    ),
                    Sequence(
                        "$",
                        "{",
                        FirstOf(
                            Sequence(
                                StringParameter(),
                                Action<Any> { true }
                            ),
                            Sequence(
                                "",
                                ""
                            )
                        ),
                        "}"
                    ),
                    Sequence(
                        AllButMatcher(charArrayOf('\\', '"')),
                        Action<Any> { str.set(str.get() + match()) }
                    )
                )
            ),
            "\"",
            Action<Any> { push(str.get()) },
            Optional(
                StringTransformFunction()
            )
        )
    }

    open fun Character(): Rule {
        val char = Var<Char>()

        return Sequence(
            "'",
            FirstOf(
                Sequence(
                    "\\",
                    FirstOf(
                        Sequence(
                            FirstOf(
                                "\'",
                                "\\",
                                "/",
                                "b",
                                "f",
                                "n",
                                "r",
                                "t"
                            ),
                            Action<Any> {
                                when (match()) {
                                    "\'" -> char.set('\'')
                                    "\\" -> char.set('\\')
                                    "/" -> char.set('/')
                                    "b" -> char.set('\b')
                                    "f" -> char.set(0xC.toChar())
                                    "n" -> char.set('\n')
                                    "r" -> char.set('\r')
                                    "t" -> char.set('\r')
                                }

                                return@Action true
                            }
                        ),
                        Sequence(
                            "u",
                            NTimes(4, Digit(16)),
                            Action<Any> { char.set(match().toInt(16).toChar()) }
                        )
                    )
                ),
                Sequence(
                    AllButMatcher(charArrayOf('\\', '\'')),
                    Action<Any> { char.set(matchedChar()) }
                )
            ),
            "'",
            Action<Any> { push(char.get()) }
        )
    }

    open fun StringTransformFunction(): Rule {
        val stringVar = StringVar()

        return Sequence(
            Action<Any> { stringVar.set(pop() as String) },
            ZeroOrMore(
                '.',
                FirstOf(
                    Sequence(
                        Parameter("trim", FirstOf(CharArrayRule(), Action<Any> { push(charArrayOf()) })),
                        Action<Any> {
                            val charArray = (pop() as CharArray)
                            stringVar.set(if (charArray.isEmpty()) stringVar.get().trim() else stringVar.get().trim(*charArray))
                        }
                    ),
                    ""
                )
            ),
            Action<Any> { push(stringVar.get()) }
        )
    }

    open fun CharArrayRule(): Rule {
        val arrayVar = Var<MutableList<Char>>(ArrayList())

        return Sequence(
            Action<Any> { arrayVar.get().clear(); true },
            Parameter(
                "charArrayOf",
                OneOrMany(
                    Sequence(
                        Character(),
                        Action<Any> { arrayVar.get().add(pop() as Char) }
                    ),
                    Sequence(OptionalInlineWhitespace(), Ch(','), OptionalInlineWhitespace())
                )
            ),
            Action<Any> { push(arrayVar.get().toCharArray()) }
        )
    }

    override fun toRule(obj: Any?): Rule {
        return when (obj) {
            is String -> IgnoreCase(obj)
            ';' -> Optional(Ch(';'))
            else -> super.toRule(obj)
        }
    }
}