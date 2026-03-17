package co.edu.escuelaing.controllers;

import co.edu.escuelaing.annotations.GetMapping;
import co.edu.escuelaing.annotations.RestController;

@RestController
public class HelloController {

    @GetMapping("/")
    public String index() {
        return "<h1>Greetings from MicroSpring!</h1>";
    }
}