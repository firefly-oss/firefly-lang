package com.firefly.intellij;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Map;

/**
 * Color settings page for Firefly language.
 * Allows users to customize syntax highlighting colors.
 */
public class FireflyColorSettingsPage implements ColorSettingsPage {
    
    private static final AttributesDescriptor[] DESCRIPTORS = new AttributesDescriptor[]{
            // Keywords
            new AttributesDescriptor("Keyword", FireflySyntaxHighlighter.KEYWORD),
            new AttributesDescriptor("Keyword//Control Flow", FireflySyntaxHighlighter.KEYWORD_CONTROL),
            new AttributesDescriptor("Keyword//Declaration", FireflySyntaxHighlighter.KEYWORD_DECLARATION),
            new AttributesDescriptor("Keyword//Modifier", FireflySyntaxHighlighter.KEYWORD_MODIFIER),

            // Literals
            new AttributesDescriptor("String", FireflySyntaxHighlighter.STRING),
            new AttributesDescriptor("Number", FireflySyntaxHighlighter.NUMBER),
            new AttributesDescriptor("Boolean", FireflySyntaxHighlighter.BOOLEAN),
            new AttributesDescriptor("Null", FireflySyntaxHighlighter.NULL),

            // Comments
            new AttributesDescriptor("Comment//Line Comment", FireflySyntaxHighlighter.LINE_COMMENT),
            new AttributesDescriptor("Comment//Block Comment", FireflySyntaxHighlighter.BLOCK_COMMENT),

            // Operators
            new AttributesDescriptor("Operator", FireflySyntaxHighlighter.OPERATOR),

            // Identifiers
            new AttributesDescriptor("Identifier", FireflySyntaxHighlighter.IDENTIFIER),
            new AttributesDescriptor("Type Identifier", FireflySyntaxHighlighter.TYPE_IDENTIFIER),
            new AttributesDescriptor("Function//Declaration", FireflySyntaxHighlighter.FUNCTION_DECLARATION),
            new AttributesDescriptor("Function//Call", FireflySyntaxHighlighter.FUNCTION_CALL),
            new AttributesDescriptor("Parameter", FireflySyntaxHighlighter.PARAMETER),
            new AttributesDescriptor("Annotation", FireflySyntaxHighlighter.ANNOTATION),

            // Punctuation
            new AttributesDescriptor("Braces and Operators//Parentheses", FireflySyntaxHighlighter.PARENTHESES),
            new AttributesDescriptor("Braces and Operators//Braces", FireflySyntaxHighlighter.BRACES),
            new AttributesDescriptor("Braces and Operators//Brackets", FireflySyntaxHighlighter.BRACKETS),
            new AttributesDescriptor("Braces and Operators//Comma", FireflySyntaxHighlighter.COMMA),
            new AttributesDescriptor("Braces and Operators//Semicolon", FireflySyntaxHighlighter.SEMICOLON),
            new AttributesDescriptor("Braces and Operators//Dot", FireflySyntaxHighlighter.DOT),
    };
    
    @Nullable
    @Override
    public Icon getIcon() {
        return FireflyIcons.FILE;
    }
    
    @NotNull
    @Override
    public SyntaxHighlighter getHighlighter() {
        return new FireflySyntaxHighlighter();
    }
    
    @NotNull
    @Override
    public String getDemoText() {
        return """
                // Firefly Language Demo - Complete Syntax Showcase
                package com::example::demo

                import firefly::std::collections::PersistentList
                import firefly::std::option::Option

                /**
                 * A comprehensive Firefly demo showcasing all language features.
                 */
                @RestController
                @RequestMapping("/api/users")
                class UserService {
                    let mut users: PersistentList<User>;

                    init() {
                        users = PersistentList.empty();
                    }

                    fn getUsers() -> PersistentList<User> {
                        return users;
                    }

                    async fn createUser(name: String, age: Int) -> Result<User, String> {
                        if age < 0 {
                            return Err("Invalid age");
                        }

                        let user = new User(name, age);
                        users = users.cons(user);
                        return Ok(user);
                    }
                }

                // Data types (ADTs)
                data Result<T, E> {
                    Ok(T),
                    Err(E)
                }

                data Option<T> {
                    Some(T),
                    None
                }

                // Struct types
                struct Point {
                    x: Float,
                    y: Float
                }

                // Trait definition
                trait Printable {
                    fn print() -> Unit;
                }

                // Actor for concurrency
                actor Counter {
                    let mut count: Int = 0;

                    receive {
                        case Increment -> count = count + 1,
                        case Decrement -> count = count - 1,
                        case GetCount(sender) -> sender >> count
                    }
                }

                // Main function with various features
                async fn main() -> Unit {
                    // Numbers: hex, binary, octal, float
                    let hex = 0xFF;
                    let binary = 0b1010;
                    let octal = 0o755;
                    let float = 3.14159;
                    let scientific = 1.5e-10;

                    // Collections and lambdas
                    let numbers = PersistentList.of(1, 2, 3, 4, 5);
                    let doubled = numbers.map(|n| n * 2);
                    let filtered = numbers.filter(|n| n > 3);

                    // Pattern matching
                    match doubled.head() {
                        Some(value) -> println("First: " + value),
                        None -> println("Empty list")
                    }

                    // Control flow
                    for num in numbers {
                        if num > 3 {
                            println("Large: " + num);
                        } else if num == 3 {
                            println("Medium: " + num);
                        } else {
                            println("Small: " + num);
                        }
                    }

                    // Ranges
                    for i in 0..10 {
                        println(i);
                    }

                    // Async/await
                    let result = await fetchData();

                    // Error handling
                    try {
                        riskyOperation();
                    } catch (e) {
                        println("Error: " + e);
                    } finally {
                        cleanup();
                    }

                    // Null safety operators
                    let optional: String? = null;
                    let value = optional ?? "default";
                    let length = optional?.length() ?: 0;

                    // Actor messaging
                    let counter = spawn Counter();
                    counter >> Increment;

                    // Concurrent operations
                    concurrent {
                        task1(),
                        task2(),
                        task3()
                    }

                    // Race condition
                    let winner = race {
                        slowOperation(),
                        fastOperation()
                    }
                }

                // Boolean and null literals
                let isActive: Boolean = true;
                let isDisabled: Boolean = false;
                let nothing: Unit = null;
                """;
    }
    
    @Nullable
    @Override
    public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
        return null;
    }
    
    @NotNull
    @Override
    public AttributesDescriptor[] getAttributeDescriptors() {
        return DESCRIPTORS;
    }

    @NotNull
    @Override
    public ColorDescriptor[] getColorDescriptors() {
        return ColorDescriptor.EMPTY_ARRAY;
    }
    
    @NotNull
    @Override
    public String getDisplayName() {
        return "Firefly";
    }
}

