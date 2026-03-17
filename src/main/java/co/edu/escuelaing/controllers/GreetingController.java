package co.edu.escuelaing.controllers;

import co.edu.escuelaing.annotations.GetMapping;
import co.edu.escuelaing.annotations.RequestParam;
import co.edu.escuelaing.annotations.RestController;

@RestController
public class GreetingController {

    @GetMapping("/greeting")
    public String greeting(
        @RequestParam(value = "name", defaultValue = "World") String name) {
        return "<h1>Hola, " + name + "!</h1>";
    }
}