// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "TaskLens",
    platforms: [
        .iOS(.v15),
        .macOS(.v12)
    ],
    products: [
        .library(
            name: "TaskLens",
            targets: ["TaskLens"]
        ),
        .library(
            name: "TaskLensNoop",
            targets: ["TaskLensNoop"]
        ),
        .library(
            name: "TaskLensKourierBridge",
            targets: ["TaskLensKourierBridge"]
        )
    ],
    targets: [
        .target(
            name: "TaskLensCore",
            dependencies: [],
            path: "Sources/TaskLensCore"
        ),
        .target(
            name: "TaskLensStorage",
            dependencies: ["TaskLensCore"],
            path: "Sources/TaskLensStorage"
        ),
        .target(
            name: "TaskLensDiagnosis",
            dependencies: ["TaskLensCore"],
            path: "Sources/TaskLensDiagnosis"
        ),
        .target(
            name: "TaskLensBGTasks",
            dependencies: ["TaskLensCore"],
            path: "Sources/TaskLensBGTasks"
        ),
        .target(
            name: "TaskLensURLSession",
            dependencies: ["TaskLensCore"],
            path: "Sources/TaskLensURLSession"
        ),
        .target(
            name: "TaskLensUI",
            dependencies: ["TaskLensCore", "TaskLensDiagnosis"],
            path: "Sources/TaskLensUI"
        ),
        .target(
            name: "TaskLens",
            dependencies: [
                "TaskLensCore",
                "TaskLensStorage",
                "TaskLensDiagnosis",
                "TaskLensBGTasks",
                "TaskLensURLSession",
                "TaskLensUI"
            ],
            path: "Sources/TaskLens"
        ),
        .target(
            name: "TaskLensNoop",
            dependencies: [],
            path: "Sources/TaskLensNoop"
        ),
        .target(
            name: "TaskLensKourierBridge",
            dependencies: ["TaskLensCore"],
            path: "Sources/TaskLensKourierBridge"
        ),
        .testTarget(
            name: "TaskLensTests",
            dependencies: [
                "TaskLens",
                "TaskLensCore",
                "TaskLensDiagnosis",
                "TaskLensStorage",
                "TaskLensBGTasks",
                "TaskLensURLSession"
            ],
            path: "Tests/TaskLensTests"
        )
    ]
)
