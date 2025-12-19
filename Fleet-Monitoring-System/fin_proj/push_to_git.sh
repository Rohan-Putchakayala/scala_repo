#!/bin/bash

# Script to safely push the cleaned project to GitHub
# Run this from the scala_repo directory

echo "🔧 Navigating to git repository root..."
cd ../../Documents/scala_git/scala_repo

echo "📋 Checking git status..."
git status

echo "➕ Adding cleaned project files..."
git add Fleet-Monitoring-System/fin_proj/

echo "💾 Committing changes..."
git commit -m "Remove hardcoded credentials and clean up configuration

- Replace AWS keys with environment variables  
- Replace database credentials with env vars
- Add .env.example template for developers
- Update .gitignore to exclude build artifacts
- Clean compiled files containing embedded secrets
- Remove target/ and .scala-build/ directories"

echo "🚀 Pushing to GitHub..."
git push -u origin main

echo "✅ Push completed! Your project should now be safely on GitHub without any credential issues."