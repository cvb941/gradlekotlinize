import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLTextAreaElement
import org.w3c.dom.events.Event

fun main() {
    window.onload = {

        val groovyInput = document.getElementById("groovyInput") as HTMLTextAreaElement
        val kotlinOutput = document.getElementById("kotlinOutput") as HTMLTextAreaElement

        // Initial sample
        groovyInput.textContent = Sample.GROOVY
        kotlinOutput.textContent = GradleKotlinConverter.convert(Sample.GROOVY)

        val convert: (Event) -> dynamic = {
            kotlinOutput.textContent = GradleKotlinConverter.convert(groovyInput.value)
            Unit.asDynamic()
        }

        groovyInput.onkeyup = convert
        groovyInput.onchange = convert

        // Delete on initial click
        var clicked = false
        groovyInput.onclick = {
            if (!clicked) {
                groovyInput.textContent = ""
                kotlinOutput.textContent = ""
                clicked = true
            }
        }


        Unit.asDynamic()
    }

}

fun sendConversionEvent() {
    js(
        """
        logEvent(analytics, 'conversion', {
        });
    """
    )
}
